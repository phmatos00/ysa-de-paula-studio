package br.com.ysadepaula.estoque.controller;

import br.com.ysadepaula.estoque.dto.EntradaEstoqueRequest;
import br.com.ysadepaula.estoque.dto.SaidaEstoqueRequest;
import br.com.ysadepaula.estoque.model.MovimentacaoEstoque;
import br.com.ysadepaula.estoque.model.Produto;
import br.com.ysadepaula.estoque.service.EstoqueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estoque")
public class EstoqueController {

    private final EstoqueService estoque;

    public EstoqueController(EstoqueService estoque) {
        this.estoque = estoque;
    }

    @GetMapping
    public List<Produto> produtos() {
        return estoque.listar();
    }

    @PostMapping
    public Produto salvar(@RequestBody Produto p) {
        return estoque.salvar(p);
    }

    @DeleteMapping("/{id}")
    public void desativar(@PathVariable Long id) {
        estoque.desativar(id);
    }

    @PostMapping("/{id}/entrada")
    public Produto entrada(@PathVariable Long id, @RequestBody EntradaEstoqueRequest req) {
        return estoque.entrada(id, req.quantidade(), req.custoUnitario(), req.lancarDespesa(),
                req.formaPagamento(), req.observacao());
    }

    @PostMapping("/{id}/saida")
    public Produto saida(@PathVariable Long id, @RequestBody SaidaEstoqueRequest req) {
        return estoque.saida(id, req.quantidade(), req.observacao());
    }

    @GetMapping("/movimentacoes")
    public List<MovimentacaoEstoque> movimentacoes() {
        return estoque.ultimasMovimentacoes();
    }
}
