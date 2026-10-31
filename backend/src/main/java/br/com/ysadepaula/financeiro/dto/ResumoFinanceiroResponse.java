package br.com.ysadepaula.financeiro.dto;

import br.com.ysadepaula.financeiro.model.Lancamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ResumoFinanceiroResponse(LocalDate de, LocalDate ate, BigDecimal entradas, BigDecimal saidas,
                                       BigDecimal saldo, Map<String, BigDecimal> entradasPorCategoria,
                                       Map<String, BigDecimal> saidasPorCategoria, List<Lancamento> lancamentos) {
}
