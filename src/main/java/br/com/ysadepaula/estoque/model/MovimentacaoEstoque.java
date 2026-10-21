package br.com.ysadepaula.estoque.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    public Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TipoMovimentacao tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal quantidade;

    public LocalDateTime data = LocalDateTime.now();

    public String observacao;
}
