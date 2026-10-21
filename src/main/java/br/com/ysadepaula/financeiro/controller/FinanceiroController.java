package br.com.ysadepaula.financeiro.controller;

import br.com.ysadepaula.financeiro.dto.ResumoFinanceiroResponse;
import br.com.ysadepaula.financeiro.model.Lancamento;
import br.com.ysadepaula.financeiro.service.FinanceiroService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/financeiro")
public class FinanceiroController {

    private final FinanceiroService financeiro;

    public FinanceiroController(FinanceiroService financeiro) {
        this.financeiro = financeiro;
    }

    @GetMapping
    public ResumoFinanceiroResponse resumo(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return financeiro.resumo(de, ate);
    }

    @PostMapping
    public Lancamento salvar(@RequestBody Lancamento l) {
        return financeiro.salvar(l);
    }

    @DeleteMapping("/{id}")
    public void excluir(@PathVariable Long id) {
        financeiro.excluir(id);
    }
}
