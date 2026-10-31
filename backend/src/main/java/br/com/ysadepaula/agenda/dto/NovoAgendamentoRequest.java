package br.com.ysadepaula.agenda.dto;

import java.time.LocalDateTime;

public record NovoAgendamentoRequest(String nome, String telefone, Long servicoId, LocalDateTime inicio,
                                     String observacoes) {
}
