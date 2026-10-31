package br.com.ysadepaula.compartilhado.controller;

import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.dto.InfoStudioResponse;
import br.com.ysadepaula.compartilhado.util.Datas;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.Comparator;
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

        List<DayOfWeek> dias = studio.diasFuncionamento().stream().sorted(Comparator.comparing(DayOfWeek::getValue)).toList();
        String funcionamento = dias.isEmpty() ? "" : Datas.nomeDia(dias.getFirst())
                + (dias.size() > 1 ? " a " + Datas.nomeDia(dias.getLast()) : "")
                + ", das " + studio.abertura() + " às " + studio.fechamento();
        return new InfoStudioResponse(studio.nome(), studio.profissional(), studio.especialidade(), studio.endereco(),
                funcionamento, Cliente.normalizarTelefone(studio.whatsapp()), studio.instagram());
    }

    @GetMapping("/servicos")
    public List<Servico> servicos() {
        return servicos.findByAtivoTrueOrderByNome();
    }
}
