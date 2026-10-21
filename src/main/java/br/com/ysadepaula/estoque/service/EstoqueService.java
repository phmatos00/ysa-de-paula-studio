package br.com.ysadepaula.estoque.service;

import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.estoque.model.MovimentacaoEstoque;
import br.com.ysadepaula.estoque.model.Produto;
import br.com.ysadepaula.estoque.model.TipoMovimentacao;
import br.com.ysadepaula.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.ysadepaula.estoque.repository.ProdutoRepository;
import br.com.ysadepaula.financeiro.service.FinanceiroService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EstoqueService {

    private final ProdutoRepository produtos;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final FinanceiroService financeiro;

    public EstoqueService(ProdutoRepository produtos, MovimentacaoEstoqueRepository movimentacoes,
                          FinanceiroService financeiro) {
        this.produtos = produtos;
        this.movimentacoes = movimentacoes;
        this.financeiro = financeiro;
    }

    public List<Produto> listar() {
        return produtos.findByAtivoTrueOrderByNome();
    }

    public List<Produto> abaixoDoMinimo() {
        return listar().stream().filter(Produto::isEstoqueBaixo).toList();
    }

    public List<MovimentacaoEstoque> ultimasMovimentacoes() {
        return movimentacoes.findTop50ByOrderByDataDesc();
    }

    @Transactional
    public Produto salvar(Produto p) {
        if (p.nome == null || p.nome.isBlank()) throw new NegocioException("Informe o nome do produto.");
        if (p.id != null) {
            // a quantidade só muda por movimentação, para manter o histórico correto
            Produto atual = buscar(p.id);
            p.quantidade = atual.quantidade;
        }
        return produtos.save(p);
    }

    @Transactional
    public void desativar(Long id) {
        Produto p = buscar(id);
        p.ativo = false;
        produtos.save(p);
    }

    /**
     * Entrada de mercadoria (compra). Se {@code lancarDespesa} for verdadeiro, registra a saída
     * correspondente no financeiro.
     */
    @Transactional
    public Produto entrada(Long produtoId, BigDecimal quantidade, BigDecimal custoUnitario,
                           boolean lancarDespesa, String formaPagamento, String observacao) {
        Produto p = buscar(produtoId);
        exigirPositivo(quantidade);
        p.quantidade = p.quantidade.add(quantidade);
        if (custoUnitario != null && custoUnitario.signum() > 0) p.custoUnitario = custoUnitario;
        registrar(p, TipoMovimentacao.ENTRADA, quantidade, observacao);
        if (lancarDespesa) {
            financeiro.registrarCompraMaterial("Compra: " + quantidade.stripTrailingZeros().toPlainString()
                    + " " + p.unidade + " de " + p.nome, p.custoUnitario.multiply(quantidade), formaPagamento);
        }
        return produtos.save(p);
    }

    /** Saída por uso nos atendimentos, perda ou vencimento. */
    @Transactional
    public Produto saida(Long produtoId, BigDecimal quantidade, String observacao) {
        Produto p = buscar(produtoId);
        exigirPositivo(quantidade);
        if (quantidade.compareTo(p.quantidade) > 0) {
            throw new NegocioException("Estoque insuficiente de " + p.nome + " (disponível: "
                    + p.quantidade.stripTrailingZeros().toPlainString() + " " + p.unidade + ").");
        }
        p.quantidade = p.quantidade.subtract(quantidade);
        registrar(p, TipoMovimentacao.SAIDA, quantidade, observacao);
        return produtos.save(p);
    }

    private void registrar(Produto p, TipoMovimentacao tipo, BigDecimal qtd, String obs) {
        MovimentacaoEstoque m = new MovimentacaoEstoque();
        m.produto = p;
        m.tipo = tipo;
        m.quantidade = qtd;
        m.observacao = obs;
        movimentacoes.save(m);
    }

    private Produto buscar(Long id) {
        return produtos.findById(id).orElseThrow(() -> new NegocioException("Produto " + id + " não encontrado."));
    }

    private static void exigirPositivo(BigDecimal q) {
        if (q == null || q.signum() <= 0) throw new NegocioException("Informe uma quantidade maior que zero.");
    }
}
