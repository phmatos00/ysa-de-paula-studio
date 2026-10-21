package br.com.ysadepaula.servico.controller;

import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    private final ServicoRepository servicos;

    public ServicoController(ServicoRepository servicos) {
        this.servicos = servicos;
    }

    @GetMapping
    public List<Servico> listar() {
        return servicos.findByAtivoTrueOrderByNome();
    }

    @PostMapping
    public Servico salvar(@RequestBody Servico s) {
        if (s.nome == null || s.nome.isBlank()) throw new NegocioException("Informe o nome do serviço.");
        if (s.duracaoMinutos <= 0) throw new NegocioException("Informe a duração em minutos.");
        s.ativo = true;
        return servicos.save(s);
    }

    /** Desativa em vez de apagar, para manter o histórico dos agendamentos. */
    @DeleteMapping("/{id}")
    public void desativar(@PathVariable Long id) {
        servicos.findById(id).ifPresent(s -> {
            s.ativo = false;
            servicos.save(s);
        });
    }
}
