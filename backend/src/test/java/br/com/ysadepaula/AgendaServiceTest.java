package br.com.ysadepaula;

import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.model.OrigemAgendamento;
import br.com.ysadepaula.agenda.model.StatusAgendamento;
import br.com.ysadepaula.agenda.service.AgendaService;
import br.com.ysadepaula.atendimento.ferramenta.FerramentasAtendimento;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.estoque.service.EstoqueService;
import br.com.ysadepaula.financeiro.repository.LancamentoRepository;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private LocalDate proximoDiaUtil() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (!studio.diasFuncionamento().contains(d.getDayOfWeek())) d = d.plusDays(1);
        return d;
    }

    private Servico servico() {
        return servicos.findByAtivoTrueOrderByNome().stream().filter(s -> s.duracaoMinutos == 120).findFirst().orElseThrow();
    }

    @Test
    void agendamentoOcupaOHorarioENaoPermiteConflito() {
        LocalDate dia = proximoDiaUtil();
        Servico s = servico();
        assertTrue(agenda.horariosDisponiveis(dia, s.id).contains(LocalTime.of(10, 0)));

        agenda.agendar("Maria Silva", "(11) 98765-4321", s.id, dia.atTime(10, 0), null, OrigemAgendamento.PAINEL);

        List<LocalTime> livres = agenda.horariosDisponiveis(dia, s.id);
        assertFalse(livres.contains(LocalTime.of(10, 0)));
        assertFalse(livres.contains(LocalTime.of(11, 30)), "início no meio de outro atendimento");
        assertFalse(livres.contains(LocalTime.of(9, 0)), "termina depois do início de outro atendimento");
        assertTrue(livres.contains(LocalTime.of(12, 0)));
        assertFalse(livres.contains(studio.fechamento().minusMinutes(60)), "não cabe antes de fechar");
        assertThrows(NegocioException.class, () ->
                agenda.agendar("Ana", "11911112222", s.id, dia.atTime(11, 0), null, OrigemAgendamento.IA));
    }

    @Test
    void concluirLancaEntradaNoFinanceiro() {
        Agendamento a = agenda.agendar("Julia", "11955554444", servico().id, proximoDiaUtil().atTime(14, 0),
                null, OrigemAgendamento.PAINEL);
        agenda.concluir(a.id, new BigDecimal("180.00"), "Pix");
        agenda.concluir(a.id, null, "Pix"); // não pode lançar em dobro
        assertTrue(lancamentos.existsByAgendamentoId(a.id));
        assertEquals(1, lancamentos.findAll().stream().filter(l -> a.id.equals(l.agendamentoId)).count());
    }

    @Test
    void ferramentaDaIaNaoMexeEmAgendamentoDeOutraCliente() {
        Agendamento a = agenda.agendar("Carla", "11933332222", servico().id, proximoDiaUtil().atTime(16, 0),
                null, OrigemAgendamento.IA);
        assertThrows(NegocioException.class, () -> ferramentas.executar("cancelar_agendamento",
                Map.of("agendamento_id", a.id, "telefone", "11900000000")));
        ferramentas.executar("confirmar_agendamento", Map.of("agendamento_id", a.id, "telefone", "11933332222"));
        assertEquals(StatusAgendamento.CONFIRMADO, agenda.buscar(a.id).status);
    }

    @Test
    void remarcarLiberaHorarioAntigo() {
        LocalDate dia = proximoDiaUtil();
        Servico s = servico();
        Agendamento a = agenda.agendar("Bruna", "11977776666", s.id, dia.atTime(9, 0), null, OrigemAgendamento.IA);
        agenda.remarcar(a.id, dia.atTime(13, 0));
        assertTrue(agenda.horariosDisponiveis(dia, s.id).contains(LocalTime.of(9, 0)));
        assertFalse(agenda.horariosDisponiveis(dia, s.id).contains(LocalTime.of(13, 0)));
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
        assertThrows(NegocioException.class, () -> agenda.agendar("X", "11911110000", servico().id,
                LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0), null,
                OrigemAgendamento.PAINEL));
    }
}
