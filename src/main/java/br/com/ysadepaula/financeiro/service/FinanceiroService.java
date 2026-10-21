package br.com.ysadepaula.financeiro.service;

import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.financeiro.dto.ResumoFinanceiroResponse;
import br.com.ysadepaula.financeiro.model.Lancamento;
import br.com.ysadepaula.financeiro.model.TipoLancamento;
import br.com.ysadepaula.financeiro.repository.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class FinanceiroService {

    private final LancamentoRepository lancamentos;

    public FinanceiroService(LancamentoRepository lancamentos) {
        this.lancamentos = lancamentos;
    }

    @Transactional
    public Lancamento salvar(Lancamento l) {
        if (l.tipo == null) throw new NegocioException("Informe se é entrada ou saída.");
        if (l.valor == null || l.valor.signum() <= 0) throw new NegocioException("Informe um valor maior que zero.");
        if (l.categoria == null || l.categoria.isBlank()) throw new NegocioException("Informe a categoria.");
        if (l.data == null) l.data = LocalDate.now();
        return lancamentos.save(l);
    }

    @Transactional
    public void excluir(Long id) {
        lancamentos.deleteById(id);
    }

    @Transactional
    public void registrarAtendimento(Agendamento a, String formaPagamento) {
        if (a.valor == null || a.valor.signum() <= 0 || lancamentos.existsByAgendamentoId(a.id)) return;
        Lancamento l = new Lancamento();
        l.tipo = TipoLancamento.ENTRADA;
        l.categoria = "Atendimento";
        l.descricao = a.servico.nome + " - " + a.cliente.nome;
        l.valor = a.valor;
        l.data = a.inicio.toLocalDate();
        l.formaPagamento = formaPagamento;
        l.agendamentoId = a.id;
        lancamentos.save(l);
    }

    @Transactional
    public void registrarCompraMaterial(String descricao, BigDecimal valor, String formaPagamento) {
        if (valor == null || valor.signum() <= 0) return;
        Lancamento l = new Lancamento();
        l.tipo = TipoLancamento.SAIDA;
        l.categoria = "Material / Estoque";
        l.descricao = descricao;
        l.valor = valor;
        l.data = LocalDate.now();
        l.formaPagamento = formaPagamento;
        lancamentos.save(l);
    }

    public ResumoFinanceiroResponse resumo(LocalDate de, LocalDate ate) {
        List<Lancamento> lista = lancamentos.findByDataBetweenOrderByDataDescIdDesc(de, ate);
        BigDecimal entradas = BigDecimal.ZERO, saidas = BigDecimal.ZERO;
        Map<String, BigDecimal> porCatEntrada = new TreeMap<>(), porCatSaida = new TreeMap<>();
        for (Lancamento l : lista) {
            if (l.tipo == TipoLancamento.ENTRADA) {
                entradas = entradas.add(l.valor);
                porCatEntrada.merge(l.categoria, l.valor, BigDecimal::add);
            } else {
                saidas = saidas.add(l.valor);
                porCatSaida.merge(l.categoria, l.valor, BigDecimal::add);
            }
        }
        return new ResumoFinanceiroResponse(de, ate, entradas, saidas, entradas.subtract(saidas), porCatEntrada, porCatSaida, lista);
    }
}
