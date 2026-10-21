package br.com.ysadepaula.dashboard.dto;

import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.estoque.model.Produto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(List<Agendamento> hoje, int pendentesConfirmacao, BigDecimal entradasMes,
                                BigDecimal saidasMes, BigDecimal saldoMes, List<Produto> estoqueBaixo) {
}
