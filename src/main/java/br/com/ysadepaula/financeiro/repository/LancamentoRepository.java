package br.com.ysadepaula.financeiro.repository;

import br.com.ysadepaula.financeiro.model.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByDataBetweenOrderByDataDescIdDesc(LocalDate de, LocalDate ate);

    boolean existsByAgendamentoId(Long agendamentoId);
}
