package br.com.ysadepaula.agenda.dto;

import java.time.LocalDateTime;

/**
 * Pedido de agendamento, vindo do painel ou da atendente virtual.
 *
 * @param manutencao true para manutenção, false (ou vazio) para colocação
 * @param domicilio  true quando a cliente quer ser atendida em casa; aí o endereço é obrigatório
 */
public record NovoAgendamentoRequest(String nome, String telefone, Long servicoId, LocalDateTime inicio,
                                     String observacoes, Boolean manutencao, Boolean domicilio,
                                     String cep, String endereco) {

    public boolean ehManutencao() {
        return Boolean.TRUE.equals(manutencao);
    }

    public boolean ehDomicilio() {
        return Boolean.TRUE.equals(domicilio);
    }
}
