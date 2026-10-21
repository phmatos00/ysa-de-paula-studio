package br.com.ysadepaula.estoque.repository;

import br.com.ysadepaula.estoque.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findByAtivoTrueOrderByNome();
}
