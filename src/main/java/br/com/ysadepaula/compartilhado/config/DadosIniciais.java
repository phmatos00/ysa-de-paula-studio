package br.com.ysadepaula.compartilhado.config;

import br.com.ysadepaula.estoque.model.Produto;
import br.com.ysadepaula.estoque.repository.ProdutoRepository;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Na primeira execução, cadastra serviços e produtos de exemplo (podem ser editados no painel). */
@Component
public class DadosIniciais implements CommandLineRunner {

    private final ServicoRepository servicos;
    private final ProdutoRepository produtos;

    public DadosIniciais(ServicoRepository servicos, ProdutoRepository produtos) {
        this.servicos = servicos;
        this.produtos = produtos;
    }

    @Override
    public void run(String... args) {
        if (servicos.count() == 0) {
            servicos.saveAll(List.of(
                    new Servico("Extensão Fio a Fio", "Um fio sintético aplicado em cada cílio natural. Efeito natural e delicado.", 120, "150.00"),
                    new Servico("Volume Brasileiro", "Fios em formato Y que dão mais volume mantendo leveza.", 120, "170.00"),
                    new Servico("Volume Russo", "Fans de vários fios ultrafinos para um olhar marcante e cheio.", 150, "200.00"),
                    new Servico("Manutenção", "Reposição dos fios em até 21 dias após a aplicação.", 90, "100.00"),
                    new Servico("Lash Lifting", "Curvatura e tintura dos cílios naturais, dura de 6 a 8 semanas.", 60, "120.00"),
                    new Servico("Remoção de Extensão", "Remoção segura dos fios sem danificar os cílios naturais.", 30, "40.00"),
                    new Servico("Design de Sobrancelhas", "Design personalizado com pinça e linha.", 30, "45.00")));
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
}
