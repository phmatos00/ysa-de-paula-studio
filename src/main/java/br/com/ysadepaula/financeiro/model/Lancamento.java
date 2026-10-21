package br.com.ysadepaula.financeiro.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Movimento financeiro: tudo o que entra (ENTRADA) e sai (SAIDA) do caixa. */
@Entity
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TipoLancamento tipo;

    @Column(nullable = false)
    public String categoria;

    public String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal valor;

    @Column(nullable = false)
    public LocalDate data;

    /** Pix, Dinheiro, Cartão de crédito, Cartão de débito... */
    public String formaPagamento;

    /** Preenchido quando o lançamento veio de um atendimento concluído. */
    public Long agendamentoId;
}
