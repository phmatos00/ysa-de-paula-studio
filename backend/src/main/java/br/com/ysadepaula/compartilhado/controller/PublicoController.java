package br.com.ysadepaula.compartilhado.controller;

import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.dto.InfoStudioResponse;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Informações abertas ao público, usadas na página inicial do studio. */
@RestController
@RequestMapping("/api/publico")
public class PublicoController {

    private final StudioProperties studio;
    private final ServicoRepository servicos;

    public PublicoController(StudioProperties studio, ServicoRepository servicos) {
        this.studio = studio;
        this.servicos = servicos;
    }

    @GetMapping("/studio")
    public InfoStudioResponse studio() {
        return new InfoStudioResponse(studio.nome(), studio.profissional(), studio.especialidade(), studio.endereco(),
                studio.descricaoDeslocamento(), studio.sinalPercentual() > 0 ? studio.sinalPercentual() : null,
                studio.politicaCancelamento(),
                studio.descricaoFuncionamento(), Cliente.normalizarTelefone(studio.whatsapp()), studio.instagram());
    }

    @GetMapping("/servicos")
    public List<Servico> servicos() {
        return servicos.findByAtivoTrueOrderByNome();
    }
}
