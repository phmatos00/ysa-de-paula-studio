package br.com.ysadepaula.atendimento.controller;

import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.service.AgendaService;
import br.com.ysadepaula.atendimento.dto.MensagemChatRequest;
import br.com.ysadepaula.atendimento.dto.RespostaChatResponse;
import br.com.ysadepaula.atendimento.service.AtendenteIA;
import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.seguranca.LimitadorTentativas;
import br.com.ysadepaula.compartilhado.util.Datas;
import br.com.ysadepaula.compartilhado.util.WhatsApp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Endpoints públicos usados pela página de chat das clientes. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Cada mensagem é uma chamada paga à IA: limita quantas o mesmo IP pode mandar em pouco tempo. */
    public static final int MAX_MENSAGENS = 20;
    public static final Duration JANELA_MENSAGENS = Duration.ofMinutes(5);

    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("EEEE, dd/MM 'às' HH:mm", Datas.PT_BR);

    private final AtendenteIA atendente;
    private final AgendaService agenda;
    private final StudioProperties studio;
    private final LimitadorTentativas mensagensPorIp = new LimitadorTentativas(MAX_MENSAGENS, JANELA_MENSAGENS);

    public ChatController(AtendenteIA atendente, AgendaService agenda, StudioProperties studio) {
        this.atendente = atendente;
        this.agenda = agenda;
        this.studio = studio;
    }

    @GetMapping("/inicio")
    public Map<String, String> inicio(@RequestParam(required = false) String tel) {
        return Map.of("conversaId", UUID.randomUUID().toString(),
                "studio", studio.nome(),
                "profissional", studio.profissional(),
                "saudacao", atendente.saudacao(tel));
    }

    @PostMapping
    public ResponseEntity<RespostaChatResponse> enviar(@RequestBody MensagemChatRequest req, HttpServletRequest http) {
        if (req.mensagem() == null || req.mensagem().isBlank())
            return ResponseEntity.ok(new RespostaChatResponse(req.conversaId(), ""));
        String id = req.conversaId() == null || req.conversaId().isBlank() ? UUID.randomUUID().toString() : req.conversaId();
        if (mensagensPorIp.registrarEExcedeu(http.getRemoteAddr()))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new RespostaChatResponse(id,
                    "Você mandou muitas mensagens em pouco tempo. Aguarde alguns minutos e tente de novo, por favor."));
        String texto = req.mensagem().length() > 2000 ? req.mensagem().substring(0, 2000) : req.mensagem();
        RespostaChatResponse resposta = RespostaChatResponse.daIa(id, atendente.responder(id, req.telefone(), texto));
        Optional<Long> agendado = atendente.agendamentoCriado(id);
        if (agendado.isPresent() && studio.whatsapp() != null && !studio.whatsapp().isBlank()) {
            resposta = resposta.comWhatsapp(linkParaProfissional(agenda.buscar(agendado.get())));
        }
        return ResponseEntity.ok(resposta);
    }

    /** Abre o WhatsApp da profissional com o resumo do agendamento, para ela finalizar com a cliente. */
    private String linkParaProfissional(Agendamento a) {
        NumberFormat reais = NumberFormat.getCurrencyInstance(Datas.PT_BR);
        String local = a.domicilio
                ? "na minha casa, " + a.enderecoAtendimento + (a.cepAtendimento == null ? "" : " - CEP " + a.cepAtendimento)
                : "no studio";
        String valor = reais.format(a.valor) + (a.taxaDeslocamento != null && a.taxaDeslocamento.signum() > 0
                ? " (com " + reais.format(a.taxaDeslocamento) + " de deslocamento)" : "");
        String texto = """
                Oi, %s! Acabei de agendar pela Sofi 💕

                Nome: %s
                Técnica: %s (%s)
                Quando: %s
                Local: %s
                Valor total: %s%s

                Quero finalizar o agendamento%s""".formatted(studio.profissional(), a.cliente.nome, a.servico.nome,
                a.manutencao ? "manutenção" : "colocação", a.inicio.format(DIA_E_HORA), local, valor,
                a.valorSinal == null ? "" : "\nSinal: " + reais.format(a.valorSinal),
                a.valorSinal == null ? "." : " e combinar o sinal.");
        // o formato de moeda do Java usa um espaço especial depois do "R$"
        return WhatsApp.link(Cliente.normalizarTelefone(studio.whatsapp()), texto.replace('\u00a0', ' '));
    }
}
