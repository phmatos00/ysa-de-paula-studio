package br.com.ysadepaula.agenda.repository;

import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    List<Agendamento> findByInicioBetweenOrderByInicio(LocalDateTime de, LocalDateTime ate);

    List<Agendamento> findByClienteTelefoneAndInicioAfterOrderByInicio(String telefone, LocalDateTime apos);

    List<Agendamento> findByStatusAndInicioBetweenOrderByInicio(StatusAgendamento status, LocalDateTime de, LocalDateTime ate);
}
