package br.com.ysadepaula.cliente.controller;

import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.cliente.repository.ClienteRepository;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository clientes;

    public ClienteController(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @GetMapping
    public List<Cliente> listar() {
        return clientes.findAll(Sort.by("nome"));
    }

    /** Atualiza nome e observações. Clientes novas são cadastradas ao criar um agendamento. */
    @PostMapping
    public Cliente salvar(@RequestBody Cliente c) {
        if (c.id == null) throw new NegocioException("Clientes novas são cadastradas ao criar um agendamento.");
        Cliente atual = clientes.findById(c.id).orElseThrow(() -> new NegocioException("Cliente não encontrada."));
        atual.nome = c.nome;
        atual.observacoes = c.observacoes;
        return clientes.save(atual);
    }
}
