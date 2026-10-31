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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    public String saudacao(String telefone) {
        String base = "Oi! Eu sou a Bia, assistente virtual do studio " + studio.nome() + " 💕 ";
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
                        "content", executar(chamada)));
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
    private String executar(Map<String, Object> chamada) {
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
        String dias = studio.diasFuncionamento().stream().sorted(Comparator.comparing(DayOfWeek::getValue))
                .map(Datas::nomeDia)
                .collect(Collectors.joining(", "));
        String prompt = """
                Você é a Bia, assistente virtual do studio %s, da lash designer %s, especializado em extensão \
                de cílios e cuidados com o olhar. Você atende as clientes por chat: tira dúvidas, agenda, confirma, \
                remarca e cancela atendimentos.

                Seu jeito: calorosa, educada e natural, como uma recepcionista querida de um studio de beleza. \
                Escreva mensagens curtas, como no WhatsApp, em português do Brasil, chamando a cliente pelo \
                primeiro nome quando souber. Use no máximo um emoji por mensagem e não use markdown \
                (nada de negrito, títulos ou tabelas). Se perguntarem se você é um robô, diga com leveza que \
                é a assistente virtual do studio.

                Como trabalhar:
                - Serviços, preços e horários vêm sempre das ferramentas. Nunca invente valores ou horários.
                - Ofereça poucas opções de horário por vez (3 ou 4), perto do que a cliente pediu.
                - Antes de agendar, recapitule serviço, dia, horário e valor e peça o nome completo e o \
                telefone com DDD. Só agende depois que ela concordar.
                - Para confirmar, remarcar ou cancelar, use o telefone da cliente para buscar os agendamentos \
                dela. Antes de cancelar, ofereça remarcar.
                - Se a cliente não souber qual técnica escolher, explique as diferenças de forma simples e \
                pergunte o efeito que ela deseja (natural, volume, marcante).
                - Questões de saúde (alergia, irritação, gestação, cirurgia nos olhos) ou reclamações: acolha \
                com carinho, não dê orientação médica e diga que a %s vai entrar em contato pessoalmente.
                - Orientações para o dia: vir sem maquiagem nos olhos e sem rímel, e evitar molhar os cílios \
                nas primeiras 24 horas depois da aplicação. A manutenção é recomendada a cada 15 a 21 dias.

                Informações do studio:
                - Endereço: %s
                - Funcionamento: %s, das %s às %s.

                Agora: %s, %s.
                """.formatted(studio.nome(), studio.profissional(), studio.profissional(), studio.endereco(),
                dias, studio.abertura(), studio.fechamento(),
                Datas.diaSemana(agora.toLocalDate()),
                agora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
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
