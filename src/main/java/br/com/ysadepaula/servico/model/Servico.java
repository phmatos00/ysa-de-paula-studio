package br.com.ysadepaula.servico.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String nome;

    @Column(length = 1000)
    public String descricao;

    public int duracaoMinutos;

    @Column(precision = 10, scale = 2)
    public BigDecimal preco;

    public boolean ativo = true;

    public Servico() {
    }

    public Servico(String nome, String descricao, int duracaoMinutos, String preco) {
        this.nome = nome;
        this.descricao = descricao;
        this.duracaoMinutos = duracaoMinutos;
        this.preco = new BigDecimal(preco);
    }
}
