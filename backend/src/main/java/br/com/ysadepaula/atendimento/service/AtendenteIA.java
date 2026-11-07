package br.com.ysadepaula.atendimento.service;

import br.com.ysadepaula.atendimento.ferramenta.FerramentasAtendimento;
import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.compartilhado.util.Datas;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atendente virtual das clientes. Usa um modelo de IA com ferramentas (function calling): a cada mensagem da
 * cliente, o modelo pode consultar serviços e horários, agendar, confirmar, remarcar ou cancelar, e então responde.
 * <p>
 * Fala com qualquer API compatível com a da OpenAI (Google Gemini, Groq, OpenAI...). O endereço fica em
 * {@code studio.ia-url}, o modelo em {@code studio.modelo-ia} e a chave na variável de ambiente IA_API_KEY.
 */
@Service
public class AtendenteIA {

    private static final Logger log = LoggerFactory.getLogger(AtendenteIA.class);
    private static final int MAX_PASSOS_FERRAMENTAS = 10;
    private static final int TENTATIVAS_API = 2;
    private static final Duration EXPIRACAO_CONVERSA = Duration.ofHours(12);

    private static final String MSG_INDISPONIVEL = "Ai, tive um probleminha técnico aqui 😕 Pode tentar de novo "
            + "em alguns instantes? Se preferir, chame a gente direto pelo WhatsApp.";

    private static final class Conversa {
        final List<Map<String, Object>> mensagens = new ArrayList<>();
        String telefone;
        Instant ultimoAcesso = Instant.now();
        /** Agendamento que a IA acabou de criar e que a página ainda não recebeu. */
        Long agendamentoNovo;
    }

    private final Map<String, Conversa> conversas = new ConcurrentHashMap<>();
    private final FerramentasAtendimento ferramentas;
    private final StudioProperties studio;
    private final ObjectMapper json;
    private final RestClient http;

    public AtendenteIA(FerramentasAtendimento ferramentas, StudioProperties studio, ObjectMapper json,
                       RestClient.Builder http) {
        this.ferramentas = ferramentas;
        this.studio = studio;
        this.json = json;
        this.http = http.baseUrl(studio.iaUrl()).build();
    }

    /**
     * ID do agendamento que a IA criou na última resposta desta conversa. Devolve uma vez só:
     * a página mostra o botão do WhatsApp logo depois do agendamento, e não nas mensagens seguintes.
     */
    public Optional<Long> agendamentoCriado(String conversaId) {
        Conversa conversa = conversas.get(conversaId);
        if (conversa == null) return Optional.empty();
        synchronized (conversa) {
            Long id = conversa.agendamentoNovo;
            conversa.agendamentoNovo = null;
            return Optional.ofNullable(id);
        }
    }

    public String saudacao(String telefone) {
        String base = "Oi! Eu sou a Sofi, assistente virtual do studio " + studio.nome() + " 💕 ";
        return telefone == null || telefone.isBlank()
                ? base + "Posso te ajudar a agendar um horário, tirar dúvidas sobre os serviços ou ver seus agendamentos. Como posso te ajudar?"
                : base + "Vi que você veio pelo lembrete do seu horário. Quer que eu confirme sua presença?";
    }

    /** Processa a mensagem da cliente e devolve a resposta da atendente. */
    public String responder(String conversaId, String telefone, String mensagemCliente) {
        Conversa conversa = conversas.computeIfAbsent(conversaId, id -> new Conversa());
        synchronized (conversa) {
            conversa.ultimoAcesso = Instant.now();
            if (telefone != null && !telefone.isBlank()) conversa.telefone = Cliente.normalizarTelefone(telefone);

            int tamanhoAntes = conversa.mensagens.size();
            conversa.mensagens.add(Map.of("role", "user", "content", mensagemCliente));
            try {
                return executarTurno(conversa);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 429) log.warn("Limite de uso da API de IA atingido");
                else log.error("Erro da API de IA ({}): {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            } catch (ResourceAccessException e) {
                log.error("Falha ao chamar a API de IA (verifique a internet e studio.ia-url)", e);
            } catch (IllegalStateException e) {
                log.error(e.getMessage());
            }
            // desfaz o turno incompleto para a conversa continuar válida na próxima mensagem
            conversa.mensagens.subList(tamanhoAntes, conversa.mensagens.size()).clear();
            return MSG_INDISPONIVEL;
        }
    }

    private String executarTurno(Conversa conversa) {
        String chave = System.getenv("IA_API_KEY");
        if (chave == null || chave.isBlank()) {
            throw new IllegalStateException("Variável de ambiente IA_API_KEY não definida: o chat não vai responder");
        }
        for (int passo = 0; passo < MAX_PASSOS_FERRAMENTAS; passo++) {
            List<Map<String, Object>> mensagens = new ArrayList<>();
            mensagens.add(Map.of("role", "system", "content", promptSistema(conversa.telefone)));
            mensagens.addAll(conversa.mensagens);

            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("messages", mensagens);
            corpo.put("tools", ferramentas.definicoes());

            Map<String, Object> mensagem = mensagemDa(chamarApi(corpo, chave));
            // guarda a mensagem como veio, para a API reconhecer as próprias chamadas de ferramenta depois
            conversa.mensagens.add(mensagem);

            List<Map<String, Object>> chamadas = lista(mensagem.get("tool_calls"));
            if (chamadas.isEmpty()) {
                Object texto = mensagem.get("content");
                String t = texto == null ? "" : texto.toString().trim();
                return t.isEmpty() ? "Pode me contar um pouquinho mais? 😊" : t;
            }

            for (Map<String, Object> chamada : chamadas) {
                conversa.mensagens.add(Map.of("role", "tool",
                        "tool_call_id", String.valueOf(chamada.get("id")),
                        "content", executar(conversa, chamada)));
            }
        }
        return "Desculpa a demora! Pode repetir o que você precisa, por favor? 🙏";
    }

    /**
     * Chama a API. Se o modelo estiver sobrecarregado (503) ou no limite (429), espera um pouco e tenta de novo;
     * se continuar assim, passa para o modelo reserva.
     */
    private Map<String, Object> chamarApi(Map<String, Object> corpo, String chave) {
        List<String> modelos = new ArrayList<>(List.of(studio.modeloIa()));
        String reserva = studio.modeloIaReserva();
        if (reserva != null && !reserva.isBlank() && !reserva.equals(studio.modeloIa())) modelos.add(reserva);

        RestClientResponseException ultimoErro = null;
        for (String modelo : modelos) {
            corpo.put("model", modelo);
            for (int tentativa = 1; tentativa <= TENTATIVAS_API; tentativa++) {
                try {
                    return http.post().uri("/chat/completions")
                            .header("Authorization", "Bearer " + chave)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(corpo)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {
                            });
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    if (status != 503 && status != 429) throw e;
                    ultimoErro = e;
                    log.warn("Modelo {} respondeu {} ({}/{})", modelo, status, tentativa, TENTATIVAS_API);
                    if (tentativa < TENTATIVAS_API) esperar(1000L * tentativa);
                }
            }
        }
        throw ultimoErro;
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mensagemDa(Map<String, Object> resposta) {
        List<Map<String, Object>> escolhas = resposta == null ? List.of() : lista(resposta.get("choices"));
        if (escolhas.isEmpty() || !(escolhas.get(0).get("message") instanceof Map<?, ?> m)) {
            throw new IllegalStateException("Resposta da API de IA sem mensagem: " + resposta);
        }
        Map<String, Object> mensagem = new LinkedHashMap<>((Map<String, Object>) m);
        mensagem.put("role", "assistant");
        return mensagem;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor) {
        return valor instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private String executar(Conversa conversa, Map<String, Object> chamada) {
        Map<String, Object> funcao = chamada.get("function") instanceof Map<?, ?> f ? (Map<String, Object>) f : Map.of();
        String nome = String.valueOf(funcao.get("name"));
        try {
            Object argumentos = funcao.get("arguments");
            Map<String, Object> entrada = argumentos == null || argumentos.toString().isBlank()
                    ? Map.of()
                    : json.readValue(argumentos.toString(), new TypeReference<Map<String, Object>>() {
                    });
            Object saida = ferramentas.executar(nome, entrada == null ? Map.of() : entrada);
            log.info("IA usou {} {}", nome, entrada);
            if ("agendar".equals(nome) && saida instanceof Map<?, ?> m && m.get("agendamento_id") instanceof Long id) {
                conversa.agendamentoNovo = id;
            }
            return json.writeValueAsString(saida);
        } catch (NegocioException e) {
            return "Erro: " + e.getMessage();
        } catch (Exception e) {
            log.error("Erro ao executar a ferramenta {}", nome, e);
            return "Erro interno ao executar a ação: " + e.getMessage();
        }
    }

    private String promptSistema(String telefoneConhecido) {
        LocalDateTime agora = LocalDateTime.now();
        String prompt = """
                Você é a Sofi, assistente virtual do studio %s, da lash designer %s, especializado em extensão \
                de cílios e cuidados com o olhar. Você atende as clientes por chat: tira dúvidas, agenda, confirma, \
                remarca e cancela atendimentos.

                Seu jeito: calorosa, educada e natural, como uma recepcionista querida de um studio de beleza. \
                Escreva mensagens curtas, como no WhatsApp, em português do Brasil, chamando a cliente pelo \
                primeiro nome quando souber. Use no máximo um emoji por mensagem e não use markdown \
                (nada de negrito, títulos ou tabelas). Se perguntarem se você é um robô, diga com leveza que \
                é a assistente virtual do studio.

                Como trabalhar:
                - Serviços, preços e horários vêm sempre das ferramentas. Nunca invente valores ou horários.
                - Ofereça até 8 opções de horário por vez, perto do que a cliente pediu.

                Botões de escolha (muito importante):
                A cliente responde clicando em botões, sem precisar digitar. Sempre que ela tiver que escolher \
                entre opções, termine a mensagem com uma última linha exatamente neste formato:
                OPCOES: opção 1 | opção 2 | opção 3
                - Use para: o que ela quer fazer, a técnica (os nomes vindos de listar_servicos), colocação ou \
                manutenção, studio ou em casa, o dia (ex.: "Sábado 14/11", ofereça os próximos dias em que o \
                studio abre), o horário (ex.: "18:40"), confirmar ou corrigir o resumo, e perguntas de sim ou não.
                - Cada opção é curta (até 40 letras) e é exatamente o que a cliente diria ao escolher. No máximo \
                12 opções. Não repita as opções no texto da mensagem: os botões já mostram.
                - Quando pedir algo que só ela sabe (nome, telefone, endereço, CEP), não coloque a linha OPCOES.
                - Ao apresentar as técnicas, seja breve e deixe os nomes nos botões; quando ela escolher uma, \
                conte a descrição e os preços de colocação e manutenção vindos de listar_servicos.
                - Cada técnica tem dois tipos de atendimento: colocação (aplicação nova) e manutenção \
                (reposição dos fios), com preços e durações diferentes. Pergunte qual dos dois ela quer.
                - O atendimento pode ser no studio ou na casa da cliente (domicílio). Pergunte onde ela prefere. \
                Se for em casa, peça o endereço completo e o CEP. A taxa de deslocamento depende do dia (o ponto \
                de saída muda entre dias de semana e fim de semana): depois que ela escolher o dia, use \
                calcular_deslocamento com essa data e conte a taxa (ou que não há taxa) antes do resumo.
                - Antes de agendar, recapitule técnica, tipo, local, dia, horário e valor e peça o nome completo \
                e o telefone com DDD. No domicílio, o valor é o da técnica mais a taxa de deslocamento: mostre \
                a soma (ex.: "R$ 140,00 + R$ 15,00 de deslocamento = R$ 155,00"). Só agende depois que ela concordar.
                - Toda colocação pede um sinal de %d%% do preço da técnica para garantir o horário (manutenção \
                não tem sinal). Avise no resumo, antes de agendar. Depois de agendar, diga o valor do sinal e \
                como pagar, usando o que a ferramenta agendar devolver em "sinal" e "como_pagar_sinal". \
                O restante é pago no dia do atendimento.
                - Regra de cancelamento (avise com carinho, no resumo antes de agendar, sempre que houver sinal \
                ou taxa de deslocamento, e também se ela perguntar ou quiser cancelar): %s.
                - Logo depois de agendar, a página mostra um botão para a cliente falar com a %s no WhatsApp, \
                já com o resumo do agendamento. Termine dizendo, numa frase curta, para ela tocar no botão abaixo \
                e finalizar o atendimento com a %s. Nessa mensagem não coloque a linha OPCOES.
                - Para confirmar, remarcar ou cancelar, use o telefone da cliente para buscar os agendamentos \
                dela. Antes de cancelar, ofereça remarcar e lembre a regra de cancelamento.
                - Se a cliente não souber qual técnica escolher, explique as diferenças de forma simples e \
                pergunte o efeito que ela deseja (natural, volume, marcante).
                - Questões de saúde (alergia, irritação, gestação, cirurgia nos olhos) ou reclamações: acolha \
                com carinho, não dê orientação médica e diga que a %s vai entrar em contato pessoalmente.
                - Orientações para o dia: vir sem maquiagem nos olhos e sem rímel, e evitar molhar os cílios \
                nas primeiras 24 horas depois da aplicação. A manutenção é recomendada a cada 15 a 21 dias.

                Informações do studio:
                - Endereço: %s
                - Funcionamento: %s.
                - Taxa de deslocamento do atendimento a domicílio: %s.

                Agora: %s, %s.
                """.formatted(studio.nome(), studio.profissional(), studio.sinalPercentual(),
                studio.politicaCancelamento(), studio.profissional(), studio.profissional(), studio.profissional(),
                studio.endereco(),
                studio.descricaoFuncionamento(), studio.descricaoDeslocamento(),
                Datas.diaSemana(agora.toLocalDate()),
                agora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        if (studio.whatsapp() != null && !studio.whatsapp().isBlank()) {
            prompt += "- WhatsApp do studio: " + studio.whatsapp() + "\n";
        }
        if (studio.instagram() != null && !studio.instagram().isBlank()) {
            prompt += "- Instagram: @" + studio.instagram() + "\n";
        }
        if (telefoneConhecido != null) {
            prompt += "\nA cliente abriu o chat pelo link de lembrete enviado ao telefone " + telefoneConhecido
                    + ". Use esse telefone para buscar os agendamentos dela e ajudar a confirmar a presença.\n";
        }
        return prompt;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    void limparConversasAntigas() {
        Instant limite = Instant.now().minus(EXPIRACAO_CONVERSA);
        conversas.entrySet().removeIf(e -> e.getValue().ultimoAcesso.isBefore(limite));
    }
}
