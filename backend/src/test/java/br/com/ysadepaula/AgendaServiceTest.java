package br.com.ysadepaula;

import br.com.ysadepaula.agenda.dto.NovoAgendamentoRequest;
import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.model.OrigemAgendamento;
import br.com.ysadepaula.agenda.model.StatusAgendamento;
import br.com.ysadepaula.agenda.service.AgendaService;
import br.com.ysadepaula.atendimento.ferramenta.FerramentasAtendimento;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.deslocamento.service.DistanciaService;
import br.com.ysadepaula.estoque.service.EstoqueService;
import br.com.ysadepaula.financeiro.repository.LancamentoRepository;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:teste")
@Transactional
class AgendaServiceTest {

    @Autowired AgendaService agenda;
    @Autowired EstoqueService estoque;
    @Autowired ServicoRepository servicos;
    @Autowired LancamentoRepository lancamentos;
    @Autowired FerramentasAtendimento ferramentas;
    @Autowired StudioProperties studio;

    /** Sábado abre o dia todo, então os testes cabem com folga. */
    private LocalDate proximoDiaUtil() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.SATURDAY) d = d.plusDays(1);
        return d;
    }

    private NovoAgendamentoRequest pedido(String nome, String telefone, Long servicoId, LocalDateTime inicio) {
        return new NovoAgendamentoRequest(nome, telefone, servicoId, inicio, null, false, false, null, null);
    }

    private List<LocalTime> livres(LocalDate dia, Servico s) {
        return agenda.horariosDisponiveis(dia, s.id, false);
    }

    private Servico servico() {
        return servicos.findByAtivoTrueOrderByNome().stream().filter(s -> s.duracaoMinutos == 120).findFirst().orElseThrow();
    }

    @Test
    void agendamentoOcupaOHorarioENaoPermiteConflito() {
        LocalDate dia = proximoDiaUtil();
        Servico s = servico();
        assertTrue(livres(dia, s).contains(LocalTime.of(10, 0)));

        agenda.agendar(pedido("Maria Silva", "(11) 98765-4321", s.id, dia.atTime(10, 0)), OrigemAgendamento.PAINEL);

        List<LocalTime> livres = livres(dia, s);
        assertFalse(livres.contains(LocalTime.of(10, 0)));
        assertFalse(livres.contains(LocalTime.of(11, 30)), "início no meio de outro atendimento");
        assertFalse(livres.contains(LocalTime.of(9, 0)), "termina depois do início de outro atendimento");
        assertTrue(livres.contains(LocalTime.of(12, 0)));
        assertFalse(livres.contains(LocalTime.of(20, 0)), "não cabe antes de fechar");
        assertThrows(NegocioException.class, () ->
                agenda.agendar(pedido("Ana", "11911112222", s.id, dia.atTime(11, 0)), OrigemAgendamento.IA));
    }

    @Test
    void concluirLancaEntradaNoFinanceiro() {
        Agendamento a = agenda.agendar(pedido("Julia", "11955554444", servico().id, proximoDiaUtil().atTime(14, 0)),
                OrigemAgendamento.PAINEL);
        agenda.concluir(a.id, new BigDecimal("180.00"), "Pix");
        agenda.concluir(a.id, null, "Pix"); // não pode lançar em dobro
        assertTrue(lancamentos.existsByAgendamentoId(a.id));
        assertEquals(1, lancamentos.findAll().stream().filter(l -> a.id.equals(l.agendamentoId)).count());
    }

    @Test
    void ferramentaDaIaNaoMexeEmAgendamentoDeOutraCliente() {
        Agendamento a = agenda.agendar(pedido("Carla", "11933332222", servico().id, proximoDiaUtil().atTime(16, 0)),
                OrigemAgendamento.IA);
        assertThrows(NegocioException.class, () -> ferramentas.executar("cancelar_agendamento",
                Map.of("agendamento_id", a.id, "telefone", "11900000000")));
        ferramentas.executar("confirmar_agendamento", Map.of("agendamento_id", a.id, "telefone", "11933332222"));
        assertEquals(StatusAgendamento.CONFIRMADO, agenda.buscar(a.id).status);
    }

    @Test
    void remarcarLiberaHorarioAntigo() {
        LocalDate dia = proximoDiaUtil();
        Servico s = servico();
        Agendamento a = agenda.agendar(pedido("Bruna", "11977776666", s.id, dia.atTime(9, 0)), OrigemAgendamento.IA);
        agenda.remarcar(a.id, dia.atTime(13, 0));
        assertTrue(livres(dia, s).contains(LocalTime.of(9, 0)));
        assertFalse(livres(dia, s).contains(LocalTime.of(13, 0)));
    }

    @Test
    void saidaDeEstoqueNaoDeixaFicarNegativo() {
        var produto = estoque.listar().getFirst();
        BigDecimal atual = produto.quantidade;
        assertThrows(NegocioException.class, () -> estoque.saida(produto.id, atual.add(BigDecimal.ONE), "teste"));
        estoque.entrada(produto.id, new BigDecimal("2"), new BigDecimal("10"), true, "Pix", "teste");
        assertEquals(0, atual.add(new BigDecimal("2")).compareTo(estoque.listar().stream()
                .filter(p -> p.id.equals(produto.id)).findFirst().orElseThrow().quantidade));
    }

    @Test
    void naoAgendaNoPassado() {
        assertThrows(NegocioException.class, () -> agenda.agendar(pedido("X", "11911110000", servico().id,
                LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)), OrigemAgendamento.PAINEL));
    }

    @Test
    void manutencaoUsaPrecoEDuracaoProprios() {
        Servico fox = servicos.findByAtivoTrueOrderByNome().stream().filter(x -> x.nome.equals("Fox Yes")).findFirst().orElseThrow();
        LocalDate dia = proximoDiaUtil();
        Agendamento a = agenda.agendar(new NovoAgendamentoRequest("Lia", "11944443333", fox.id, dia.atTime(8, 0),
                null, true, false, null, null), OrigemAgendamento.PAINEL);
        assertTrue(a.manutencao);
        assertEquals(0, new BigDecimal("90.00").compareTo(a.valor));
        assertEquals(dia.atTime(9, 30), a.fim);
    }

    @Test
    void diaDeSemanaSoTemHorarioANoite() {
        LocalDate segunda = LocalDate.now().plusDays(1);
        while (segunda.getDayOfWeek() != DayOfWeek.MONDAY) segunda = segunda.plusDays(1);
        assertEquals(List.of(LocalTime.of(18, 40)), livres(segunda, servico()), "colocação de 2h só cabe às 18:40");
        assertEquals(List.of(LocalTime.of(18, 40), LocalTime.of(19, 10)),
                agenda.horariosDisponiveis(segunda, servico().id, true), "manutenção de 1h30");
        assertEquals("Segunda a sexta, das 18:40 às 21:00; sábado e domingo, das 08:00 às 21:00",
                studio.descricaoFuncionamento());
    }

    @Test
    void catalogoNovoSemSobrancelha() {
        List<String> nomes = servicos.findByAtivoTrueOrderByNome().stream().map(x -> x.nome).toList();
        assertEquals(12, nomes.size());
        assertTrue(nomes.contains("Efeito Sirena"));
        assertTrue(nomes.contains("Cílios Marrom"));
        assertFalse(nomes.contains("Efeito Molhado"));
        Servico lifting = servicos.findByAtivoTrueOrderByNome().stream().filter(x -> x.nome.equals("Lash Lifting")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(lifting.preco));
        assertFalse(lifting.temManutencao());
        assertThrows(NegocioException.class, () -> agenda.horariosDisponiveis(proximoDiaUtil(), lifting.id, true));
        assertFalse(nomes.contains("Design de Sobrancelhas"));
    }

    @Test
    void colocacaoPedeSinalDeMetadeEManutencaoNao() {
        LocalDate dia = proximoDiaUtil();
        Servico s = servico();
        Agendamento colocacao = agenda.agendar(pedido("Nina", "11966665555", s.id, dia.atTime(8, 0)), OrigemAgendamento.IA);
        assertEquals(0, s.preco.divide(new BigDecimal("2")).compareTo(colocacao.valorSinal));
        assertFalse(colocacao.sinalPago);
        agenda.registrarSinal(colocacao.id);
        assertTrue(agenda.buscar(colocacao.id).sinalPago);

        Agendamento manutencao = agenda.agendar(new NovoAgendamentoRequest("Nina", "11966665555", s.id,
                dia.atTime(12, 0), null, true, false, null, null), OrigemAgendamento.IA);
        assertNull(manutencao.valorSinal);
    }

    @Test
    void taxaDeDeslocamentoPorFaixa() {
        assertEquals(0, BigDecimal.ZERO.compareTo(studio.taxaDeslocamento(9.9)));
        assertEquals(0, new BigDecimal("15").compareTo(studio.taxaDeslocamento(10)));
        assertEquals(0, new BigDecimal("15").compareTo(studio.taxaDeslocamento(29.9)));
        assertEquals(0, new BigDecimal("30").compareTo(studio.taxaDeslocamento(30.5)));
        assertEquals("até 10 km sem taxa; de 10 a 30 km, R$ 15,00; acima de 30 km, R$ 30,00",
                studio.descricaoDeslocamento());
    }

    @Test
    void domicilioExigeEndereco() {
        assertThrows(NegocioException.class, () -> agenda.agendar(new NovoAgendamentoRequest("Rita", "11922221111",
                servico().id, proximoDiaUtil().atTime(10, 0), null, false, true, null, " "), OrigemAgendamento.IA));
    }

    @Test
    void saidaDoStudioNoFimDeSemanaEDaAquarelaNaSemana() {
        var d = new DistanciaService.Deslocamento(List.of(
                new DistanciaService.Trajeto("Studio", java.util.Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 32.4, 39L),
                new DistanciaService.Trajeto("Aquarela", java.util.Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 17.8, 23L)));
        assertEquals("Studio", d.saidaNo(DayOfWeek.SATURDAY).origem());
        assertEquals("Aquarela", d.saidaNo(DayOfWeek.MONDAY).origem());
        assertEquals(0, new BigDecimal("30").compareTo(studio.taxaDeslocamento(d.saidaNo(DayOfWeek.SUNDAY).km())));
        assertEquals(0, new BigDecimal("15").compareTo(studio.taxaDeslocamento(d.saidaNo(DayOfWeek.FRIDAY).km())));
        // dia sem ponto de saída cadastrado: usa o mais perto
        assertEquals("Aquarela", d.saidaNo(DayOfWeek.WEDNESDAY).origem());
        // e o application.yml diz: studio no fim de semana
        assertEquals(java.util.Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), studio.pontosPartida().getFirst().dias());
    }

    @Test
    void distanciaEmLinhaReta() {
        // do studio até a Aquarela Baby Gama: uns 8,9 km em linha reta
        double km = DistanciaService.linhaReta(new DistanciaService.Coordenada(-16.049881, -48.053676),
                new DistanciaService.Coordenada(-15.9707, -48.0429));
        assertEquals(8.9, km, 0.2);
    }
}
