package br.com.ysadepaula.agenda.dto;

import br.com.ysadepaula.agenda.model.Agendamento;

/** Atendimento ainda não confirmado e o link do WhatsApp com a mensagem de lembrete pronta. */
public record LembreteConfirmacaoResponse(Agendamento agendamento, String linkWhatsapp) {
}
