package br.com.ysadepaula.agenda.model;

import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.servico.model.Servico;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    public Cliente cliente;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    public Servico servico;

    @Column(nullable = false)
    public LocalDateTime inicio;

    @Column(nullable = false)
    public LocalDateTime fim;

    @Enumerated(EnumType.STRING)
    public StatusAgendamento status = StatusAgendamento.PENDENTE;

    @Enumerated(EnumType.STRING)
    public OrigemAgendamento origem = OrigemAgendamento.PAINEL;

    @Column(precision = 10, scale = 2)
    public BigDecimal valor;

    @Column(length = 1000)
    public String observacoes;

    /** false = colocação, true = manutenção. O default deixa o banco preencher as linhas que já existiam. */
    @Column(columnDefinition = "boolean default false")
    public boolean manutencao;

    /** Atendimento na casa da cliente, em vez do studio. */
    @Column(columnDefinition = "boolean default false")
    public boolean domicilio;

    @Column(length = 500)
    public String enderecoAtendimento;

    /** CEP no formato 00000-000, ou vazio se a cliente não informou. */
    @Column(length = 9)
    public String cepAtendimento;

    /** Distância de cada ponto de partida até a cliente, já em texto. Ex.: "Studio: 5,2 km (~12 min)". */
    @Column(length = 500)
    public String distancias;

    /** Já está somada em {@link #valor}; vazia quando não deu para calcular a distância. */
    @Column(precision = 10, scale = 2)
    public BigDecimal taxaDeslocamento;

    /** Sinal pedido para garantir o horário (só na colocação); vazio quando não há sinal. */
    @Column(precision = 10, scale = 2)
    public BigDecimal valorSinal;

    @Column(columnDefinition = "boolean default false")
    public boolean sinalPago;

    public LocalDateTime criadoEm = LocalDateTime.now();

    public boolean ocupaHorario() {
        return status != StatusAgendamento.CANCELADO && status != StatusAgendamento.FALTOU;
    }
}
