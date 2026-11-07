package br.com.ysadepaula.servico.repository;

import br.com.ysadepaula.servico.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    List<Servico> findByAtivoTrueOrderByNome();

    boolean existsByNomeAndAtivoTrue(String nome);

    Optional<Servico> findFirstByNomeAndPrecoAndAtivoFalse(String nome, BigDecimal preco);
}
