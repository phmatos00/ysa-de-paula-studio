package br.com.ysadepaula.estoque.dto;

import java.math.BigDecimal;

public record SaidaEstoqueRequest(BigDecimal quantidade, String observacao) {
}
