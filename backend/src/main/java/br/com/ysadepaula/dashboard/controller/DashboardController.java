package br.com.ysadepaula.dashboard.controller;

import br.com.ysadepaula.agenda.service.AgendaService;
import br.com.ysadepaula.dashboard.dto.DashboardResponse;
import br.com.ysadepaula.estoque.service.EstoqueService;
import br.com.ysadepaula.financeiro.dto.ResumoFinanceiroResponse;
import br.com.ysadepaula.financeiro.service.FinanceiroService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class DashboardController {

    private final AgendaService agenda;
    private final FinanceiroService financeiro;
    private final EstoqueService estoque;

    public DashboardController(AgendaService agenda, FinanceiroService financeiro, EstoqueService estoque) {
        this.agenda = agenda;
        this.financeiro = financeiro;
        this.estoque = estoque;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard() {
        LocalDate hoje = LocalDate.now();
        ResumoFinanceiroResponse mes = financeiro.resumo(hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()));
        return new DashboardResponse(agenda.doDia(hoje), agenda.pendentesDeConfirmacao().size(),
                mes.entradas(), mes.saidas(), mes.saldo(), estoque.abaixoDoMinimo());
    }
}
