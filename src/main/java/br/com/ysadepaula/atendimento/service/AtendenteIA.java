package br.com.ysadepaula.atendimento.service;

import br.com.ysadepaula.atendimento.ferramenta.FerramentasAtendimento;
import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.compartilhado.util.Datas;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Atendente virtual das clientes. Usa o Claude com ferramentas (tool use): a cada mensagem da cliente,
 * o modelo pode consultar serviços e horários, agendar, confirmar, remarcar ou cancelar, e então responde.
 */
@Service
public class AtendenteIA {

    private static final Logger log = LoggerFactory.getLogger(AtendenteIA.class);
    private static final int MAX_PASSOS_FERRAMENTAS = 10;
    private static final Duration EXPIRACAO_CONVERSA = Duration.ofHours(12);

    private static final String MSG_INDISPONIVEL = "Ai, tive um probleminha técnico aqui 😕 Pode tentar de novo "
            + "em alguns instantes? Se preferir, chame a gente direto pelo WhatsApp.";

    private static final class Conversa {
        final List<MessageParam> mensagens = new ArrayList<>();
        String telefone;
        Instant ultimoAcesso = Instant.now();
    }

    private static final class RespostaRecusada extends RuntimeException {
    }

    private final Map<String, Conversa> conversas = new ConcurrentHashMap<>();
    private final FerramentasAtendimento ferramentas;
    private final StudioProperties studio;
    private final ObjectMapper json;
    private volatile AnthropicClient client;

    public AtendenteIA(FerramentasAtendimento ferramentas, StudioProperties studio, ObjectMapper json) {
        this.ferramentas = ferramentas;
        this.studio = studio;
        this.json = json;
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
            conversa.mensagens.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(mensagemCliente)
                    .build());
            try {
                return executarTurno(conversa);
            } catch (RespostaRecusada e) {
                log.warn("O modelo recusou responder a esta mensagem");
            } catch (RateLimitException e) {
                log.warn("Limite de uso da API atingido", e);
            } catch (AnthropicServiceException e) {
                log.error("Erro da API ({}): {}", e.statusCode(), e.getMessage());
            } catch (AnthropicException e) {
                log.error("Falha ao chamar a API (verifique a ANTHROPIC_API_KEY e a internet)", e);
            }
            // desfaz o turno incompleto para a conversa continuar válida na próxima mensagem
            conversa.mensagens.subList(tamanhoAntes, conversa.mensagens.size()).clear();
            return MSG_INDISPONIVEL;
        }
    }

    private String executarTurno(Conversa conversa) {
        for (int passo = 0; passo < MAX_PASSOS_FERRAMENTAS; passo++) {
            Message resposta = cliente().messages().create(MessageCreateParams.builder()
                    .model(studio.modeloIa())
                    .maxTokens(16000L)
                    // conversa de chat: esforço baixo deixa as respostas rápidas e econômicas
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    // se o modelo principal recusar, o servidor tenta automaticamente um modelo alternativo
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .system(promptSistema(conversa.telefone))
                    .tools(ferramentas.definicoes().stream().map(ToolUnion::ofTool).toList())
                    .messages(conversa.mensagens)
                    .build());

            StopReason motivo = resposta.stopReason().orElse(StopReason.END_TURN);
            if (motivo.equals(StopReason.REFUSAL)) {
                throw new RespostaRecusada();
            }
            conversa.mensagens.add(resposta.toParam());

            List<ToolUseBlock> chamadas = resposta.content().stream()
                    .flatMap(b -> b.toolUse().stream()).toList();
            if (!motivo.equals(StopReason.TOOL_USE) || chamadas.isEmpty()) {
                String texto = resposta.content().stream()
                        .flatMap(b -> b.text().stream())
                        .map(TextBlock::text)
                        .collect(Collectors.joining("\n")).trim();
                return texto.isEmpty() ? "Pode me contar um pouquinho mais? 😊" : texto;
            }

            // todas as respostas das ferramentas voltam numa única mensagem
            List<ContentBlockParam> resultados = new ArrayList<>();
            for (ToolUseBlock chamada : chamadas) resultados.add(ContentBlockParam.ofToolResult(executar(chamada)));
            conversa.mensagens.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(resultados)
                    .build());
        }
        return "Desculpa a demora! Pode repetir o que você precisa, por favor? 🙏";
    }

    private ToolResultBlockParam executar(ToolUseBlock chamada) {
        ToolResultBlockParam.Builder resultado = ToolResultBlockParam.builder().toolUseId(chamada.id());
        try {
            Map<String, Object> entrada = chamada._input().convert(new TypeReference<Map<String, Object>>() {
            });
            Object saida = ferramentas.executar(chamada.name(), entrada == null ? Map.of() : entrada);
            log.info("IA usou {} {}", chamada.name(), entrada);
            return resultado.content(json.writeValueAsString(saida)).build();
        } catch (NegocioException e) {
            return resultado.content(e.getMessage()).isError(true).build();
        } catch (Exception e) {
            log.error("Erro ao executar a ferramenta {}", chamada.name(), e);
            return resultado.content("Erro interno ao executar a ação: " + e.getMessage()).isError(true).build();
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

    private AnthropicClient cliente() {
        if (client == null) {
            synchronized (this) {
                // lê ANTHROPIC_API_KEY das variáveis de ambiente
                if (client == null) client = AnthropicOkHttpClient.fromEnv();
            }
        }
        return client;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    void limparConversasAntigas() {
        Instant limite = Instant.now().minus(EXPIRACAO_CONVERSA);
        conversas.entrySet().removeIf(e -> e.getValue().ultimoAcesso.isBefore(limite));
    }
}
