package br.com.ysadepaula.estoque.repository;

import br.com.ysadepaula.estoque.model.MovimentacaoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {
    List<MovimentacaoEstoque> findTop50ByOrderByDataDesc();
}
