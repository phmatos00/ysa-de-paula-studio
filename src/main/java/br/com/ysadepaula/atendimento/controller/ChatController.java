package br.com.ysadepaula.atendimento.controller;

import br.com.ysadepaula.atendimento.dto.MensagemChatRequest;
import br.com.ysadepaula.atendimento.dto.RespostaChatResponse;
import br.com.ysadepaula.atendimento.service.AtendenteIA;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Endpoints públicos usados pela página de chat das clientes. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AtendenteIA atendente;
    private final StudioProperties studio;

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
    public RespostaChatResponse enviar(@RequestBody MensagemChatRequest req) {
        if (req.mensagem() == null || req.mensagem().isBlank()) return new RespostaChatResponse(req.conversaId(), "");
        String id = req.conversaId() == null || req.conversaId().isBlank() ? UUID.randomUUID().toString() : req.conversaId();
        String texto = req.mensagem().length() > 2000 ? req.mensagem().substring(0, 2000) : req.mensagem();
        return new RespostaChatResponse(id, atendente.responder(id, req.telefone(), texto));
    }
}
