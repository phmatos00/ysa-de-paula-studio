package br.com.ysadepaula.servico.repository;

import br.com.ysadepaula.servico.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    List<Servico> findByAtivoTrueOrderByNome();
}
