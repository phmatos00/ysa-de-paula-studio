package br.com.ysadepaula.estoque.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String nome;

    /** Fios, Cola, Descartáveis, Higienização... */
    public String categoria;

    /** un, cx, ml, pct... */
    public String unidade = "un";

    @Column(precision = 10, scale = 2)
    public BigDecimal quantidade = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    public BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    public BigDecimal custoUnitario = BigDecimal.ZERO;

    public boolean ativo = true;

    public boolean isEstoqueBaixo() {
        return quantidade.compareTo(estoqueMinimo) <= 0;
    }

    public Produto() {
    }

    public Produto(String nome, String categoria, String unidade, String quantidade, String minimo, String custo) {
        this.nome = nome;
        this.categoria = categoria;
        this.unidade = unidade;
        this.quantidade = new BigDecimal(quantidade);
        this.estoqueMinimo = new BigDecimal(minimo);
        this.custoUnitario = new BigDecimal(custo);
    }
}
