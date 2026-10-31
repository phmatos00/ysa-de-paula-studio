package br.com.ysadepaula.atendimento.controller;

import br.com.ysadepaula.atendimento.dto.MensagemChatRequest;
import br.com.ysadepaula.atendimento.dto.RespostaChatResponse;
import br.com.ysadepaula.atendimento.service.AtendenteIA;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.seguranca.LimitadorTentativas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Endpoints públicos usados pela página de chat das clientes. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Cada mensagem é uma chamada paga à IA: limita quantas o mesmo IP pode mandar em pouco tempo. */
    public static final int MAX_MENSAGENS = 20;
    public static final Duration JANELA_MENSAGENS = Duration.ofMinutes(5);

    private final AtendenteIA atendente;
    private final StudioProperties studio;
    private final LimitadorTentativas mensagensPorIp = new LimitadorTentativas(MAX_MENSAGENS, JANELA_MENSAGENS);

    public ChatController(AtendenteIA atendente, StudioProperties studio) {
        this.atendente = atendente;
        this.studio = studio;
    }

    @GetMapping("/inicio")
    public Map<String, String> inicio(@RequestParam(required = false) String tel) {
        return Map.of("conversaId", UUID.randomUUID().toString(),
                "studio", studio.nome(),
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
        return ResponseEntity.ok(new RespostaChatResponse(id, atendente.responder(id, req.telefone(), texto)));
    }
}
