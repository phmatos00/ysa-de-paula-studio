package br.com.ysadepaula.compartilhado.config;

import br.com.ysadepaula.estoque.model.Produto;
import br.com.ysadepaula.estoque.repository.ProdutoRepository;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cadastra as técnicas e os produtos iniciais (podem ser editados no painel). Os dois blocos são idempotentes:
 * rodam a cada inicialização, mas só gravam quando ainda falta o que eles cadastram.
 */
@Component
public class DadosIniciais implements CommandLineRunner {

    /** Serviço do catálogo antigo; enquanto estiver ativo, o catálogo ainda não foi trocado pelo de 2026. */
    private static final String SERVICO_DO_CATALOGO_ANTIGO = "Design de Sobrancelhas";

    private final ServicoRepository servicos;
    private final ProdutoRepository produtos;

    public DadosIniciais(ServicoRepository servicos, ProdutoRepository produtos) {
        this.servicos = servicos;
        this.produtos = produtos;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (servicos.count() == 0) {
            servicos.saveAll(catalogo());
        } else if (servicos.existsByNomeAndAtivoTrue(SERVICO_DO_CATALOGO_ANTIGO)) {
            // troca de catálogo: os serviços antigos são desativados (e não apagados) porque os
            // agendamentos já feitos continuam apontando para eles no histórico
            servicos.findByAtivoTrueOrderByNome().forEach(s -> s.ativo = false);
            servicos.saveAll(catalogo());
        }
        // nov/2026: o Lash Lifting volta por R$ 100,00, sem manutenção. O registro antigo (R$ 120,00) foi
        // desativado na troca de catálogo; reaproveita ele uma única vez, porque depois o preço já não é 120
        if (!servicos.existsByNomeAndAtivoTrue("Lash Lifting")) {
            servicos.findFirstByNomeAndPrecoAndAtivoFalse("Lash Lifting", new BigDecimal("120.00")).ifPresent(s -> {
                s.preco = new BigDecimal("100.00");
                s.duracaoMinutos = 60;
                s.precoManutencao = null;
                s.duracaoManutencaoMinutos = null;
                s.ativo = true;
            });
        }
        // nov/2026: o Cílios Marrom entra no lugar do Efeito Molhado (desativado, não apagado, por causa do histórico)
        if (servicos.existsByNomeAndAtivoTrue("Efeito Molhado")) {
            servicos.findByAtivoTrueOrderByNome().stream().filter(s -> s.nome.equals("Efeito Molhado"))
                    .forEach(s -> s.ativo = false);
            if (!servicos.existsByNomeAndAtivoTrue("Cílios Marrom")) {
                servicos.save(new Servico("Cílios Marrom", "Fios na cor marrom para um olhar marcante e mais suave "
                        + "que o preto, ótimo para quem tem cílios claros.", 120, "180.00", 90, "80.00"));
            }
        }
        if (produtos.count() == 0) {
            produtos.saveAll(List.of(
                    new Produto("Fios 0.07 C 10mm", "Fios", "cx", "4", "2", "35.00"),
                    new Produto("Fios 0.15 D mix", "Fios", "cx", "3", "2", "38.00"),
                    new Produto("Cola para extensão 5ml", "Cola", "un", "2", "1", "75.00"),
                    new Produto("Primer", "Preparação", "un", "1", "1", "30.00"),
                    new Produto("Removedor em gel", "Remoção", "un", "1", "1", "40.00"),
                    new Produto("Microbrush", "Descartáveis", "pct", "5", "2", "8.00"),
                    new Produto("Fita micropore", "Descartáveis", "un", "6", "3", "4.50"),
                    new Produto("Patch de gel (pares)", "Descartáveis", "pct", "3", "2", "25.00")));
        }
    }

    /** Técnicas de colocação: nome, descrição, duração e preço da colocação, duração e preço da manutenção. */
    private static List<Servico> catalogo() {
        return List.of(
                new Servico("Efeito Rímel", "Fios finos que imitam cílios com rímel: olhar definido e natural.", 120, "130.00", 90, "80.00"),
                new Servico("Efeito Rímel Extreme", "Versão mais intensa do efeito rímel, com fios mais marcados.", 120, "135.00", 90, "80.00"),
                new Servico("Cílios Marrom", "Fios na cor marrom para um olhar marcante e mais suave que o preto, ótimo para quem tem cílios claros.", 120, "180.00", 90, "80.00"),
                new Servico("Efeito Glow", "Efeito iluminado e leve, que abre o olhar.", 120, "145.00", 90, "80.00"),
                new Servico("Efeito Sirena", "Mais comprimento no canto externo, para um olhar alongado de sereia.", 120, "140.00", 90, "80.00"),
                new Servico("Volume Brasileiro", "Fios em formato Y que dão mais volume mantendo leveza.", 120, "140.00", 90, "80.00"),
                new Servico("Volume Inglês", "Volume cheio com fios de várias pontas.", 120, "150.00", 90, "80.00"),
                new Servico("Volume Egípcio", "Fios em formato W para um volume marcante e uniforme.", 120, "150.00", 90, "80.00"),
                new Servico("Volume Russo", "Leques de fios ultrafinos para um olhar cheio e marcante.", 120, "160.00", 90, "80.00"),
                new Servico("Mega Volume", "Leques com muitos fios ultrafinos: o volume mais intenso.", 120, "170.00", 90, "90.00"),
                new Servico("Fox Yes", "Fios alongados no canto externo que puxam o olhar, efeito raposa.", 120, "180.00", 90, "90.00"),
                new Servico("Lash Lifting", "Curvatura e tintura dos cílios naturais, dura de 6 a 8 semanas. Não tem manutenção.", 60, "100.00"));
    }
}
