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

    public LocalDateTime criadoEm = LocalDateTime.now();

    public boolean ocupaHorario() {
        return status != StatusAgendamento.CANCELADO && status != StatusAgendamento.FALTOU;
    }
}
