package br.com.ysadepaula.estoque.dto;

import java.math.BigDecimal;

public record EntradaEstoqueRequest(BigDecimal quantidade, BigDecimal custoUnitario, boolean lancarDespesa,
                                    String formaPagamento, String observacao) {
}
