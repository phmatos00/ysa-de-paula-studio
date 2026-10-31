package br.com.ysadepaula.agenda.controller;

import br.com.ysadepaula.agenda.dto.ConclusaoRequest;
import br.com.ysadepaula.agenda.dto.LembreteConfirmacaoResponse;
import br.com.ysadepaula.agenda.dto.NovoAgendamentoRequest;
import br.com.ysadepaula.agenda.dto.RemarcacaoRequest;
import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.model.OrigemAgendamento;
import br.com.ysadepaula.agenda.service.AgendaService;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.util.Datas;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    private static final DateTimeFormatter DIA_SEMANA = DateTimeFormatter.ofPattern("EEEE, dd/MM", Datas.PT_BR);

    private final AgendaService agenda;
    private final StudioProperties studio;

    public AgendaController(AgendaService agenda, StudioProperties studio) {
        this.agenda = agenda;
        this.studio = studio;
    }

    @GetMapping
    public List<Agendamento> doDia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return agenda.doDia(dia);
    }

    @GetMapping("/periodo")
    public List<Agendamento> doPeriodo(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return agenda.doPeriodo(de, ate);
    }

    @GetMapping("/horarios")
    public List<LocalTime> horarios(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                                    @RequestParam Long servicoId) {
        return agenda.horariosDisponiveis(dia, servicoId);
    }

    @PostMapping
    public Agendamento criar(@RequestBody NovoAgendamentoRequest req) {
        return agenda.agendar(req.nome(), req.telefone(), req.servicoId(), req.inicio(), req.observacoes(),
                OrigemAgendamento.PAINEL);
    }

    @PostMapping("/{id}/confirmar")
    public Agendamento confirmar(@PathVariable Long id) {
        return agenda.confirmar(id);
    }

    @PostMapping("/{id}/cancelar")
    public Agendamento cancelar(@PathVariable Long id) {
        return agenda.cancelar(id);
    }

    @PostMapping("/{id}/falta")
    public Agendamento falta(@PathVariable Long id) {
        return agenda.marcarFalta(id);
    }

    @PostMapping("/{id}/concluir")
    public Agendamento concluir(@PathVariable Long id, @RequestBody ConclusaoRequest req) {
        return agenda.concluir(id, req.valor(), req.formaPagamento());
    }

    @PostMapping("/{id}/remarcar")
    public Agendamento remarcar(@PathVariable Long id, @RequestBody RemarcacaoRequest req) {
        return agenda.remarcar(id, req.inicio());
    }

    /**
     * Atendimentos de amanhã ainda não confirmados, cada um com um link de WhatsApp que abre a conversa
     * com a cliente já com a mensagem pronta e o link do chat com a atendente virtual.
     */
    @GetMapping("/confirmacoes")
    public List<LembreteConfirmacaoResponse> confirmacoes() {
        return agenda.pendentesDeConfirmacao().stream()
                .map(a -> new LembreteConfirmacaoResponse(a, linkWhatsapp(a)))
                .toList();
    }

    private String linkWhatsapp(Agendamento a) {
        String primeiroNome = a.cliente.nome.split(" ")[0];
        String texto = "Oi, " + primeiroNome + "! Tudo bem? 💕 Passando para lembrar do seu horário de "
                + a.servico.nome + " no studio " + studio.nome() + ", " + a.inicio.format(DIA_SEMANA)
                + " às " + a.inicio.toLocalTime() + ".\n\nPode confirmar sua presença por aqui: "
                + studio.urlPublica() + "/chat.html?tel=" + a.cliente.telefone;
        return "https://wa.me/55" + a.cliente.telefone + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
