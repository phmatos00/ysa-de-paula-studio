package br.com.ysadepaula.servico.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Técnica oferecida pelo studio, com o preço da colocação e o da manutenção. */
@Entity
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String nome;

    @Column(length = 1000)
    public String descricao;

    /** Duração da colocação. */
    public int duracaoMinutos;

    /** Preço da colocação. */
    @Column(precision = 10, scale = 2)
    public BigDecimal preco;

    /** Vazio quando a técnica não tem manutenção. */
    @Column(precision = 10, scale = 2)
    public BigDecimal precoManutencao;

    public Integer duracaoManutencaoMinutos;

    public boolean ativo = true;

    public Servico() {
    }

    /** Técnica sem manutenção. */
    public Servico(String nome, String descricao, int duracaoMinutos, String preco) {
        this.nome = nome;
        this.descricao = descricao;
        this.duracaoMinutos = duracaoMinutos;
        this.preco = new BigDecimal(preco);
    }

    public Servico(String nome, String descricao, int duracaoMinutos, String preco,
                   int duracaoManutencaoMinutos, String precoManutencao) {
        this(nome, descricao, duracaoMinutos, preco);
        this.duracaoManutencaoMinutos = duracaoManutencaoMinutos;
        this.precoManutencao = new BigDecimal(precoManutencao);
    }

    public boolean temManutencao() {
        return precoManutencao != null && duracaoManutencaoMinutos != null && duracaoManutencaoMinutos > 0;
    }

    public int duracao(boolean manutencao) {
        return manutencao ? duracaoManutencaoMinutos : duracaoMinutos;
    }

    public BigDecimal preco(boolean manutencao) {
        return manutencao ? precoManutencao : preco;
    }
}
