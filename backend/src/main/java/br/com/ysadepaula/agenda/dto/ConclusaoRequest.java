package br.com.ysadepaula.agenda.dto;

import java.math.BigDecimal;

public record ConclusaoRequest(BigDecimal valor, String formaPagamento) {
}
