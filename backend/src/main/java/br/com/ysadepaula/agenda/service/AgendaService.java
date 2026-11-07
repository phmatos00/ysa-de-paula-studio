package br.com.ysadepaula.agenda.service;

import br.com.ysadepaula.agenda.dto.NovoAgendamentoRequest;
import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.model.OrigemAgendamento;
import br.com.ysadepaula.agenda.model.StatusAgendamento;
import br.com.ysadepaula.agenda.repository.AgendamentoRepository;
import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.cliente.repository.ClienteRepository;
import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.deslocamento.service.DistanciaService;
import br.com.ysadepaula.financeiro.service.FinanceiroService;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgendaService {

    private final AgendamentoRepository agendamentos;
    private final ClienteRepository clientes;
    private final ServicoRepository servicos;
    private final FinanceiroService financeiro;
    private final StudioProperties studio;
    private final DistanciaService distancia;

    public AgendaService(AgendamentoRepository agendamentos, ClienteRepository clientes,
                         ServicoRepository servicos, FinanceiroService financeiro, StudioProperties studio,
                         DistanciaService distancia) {
        this.agendamentos = agendamentos;
        this.clientes = clientes;
        this.servicos = servicos;
        this.financeiro = financeiro;
        this.studio = studio;
        this.distancia = distancia;
    }

    public List<Agendamento> doDia(LocalDate dia) {
        return agendamentos.findByInicioBetweenOrderByInicio(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
    }

    public List<Agendamento> doPeriodo(LocalDate de, LocalDate ate) {
        return agendamentos.findByInicioBetweenOrderByInicio(de.atStartOfDay(), ate.plusDays(1).atStartOfDay());
    }

    /** Próximos agendamentos (a partir de agora) da cliente com este telefone. */
    public List<Agendamento> proximosDaCliente(String telefone) {
        return agendamentos.findByClienteTelefoneAndInicioAfterOrderByInicio(
                Cliente.normalizarTelefone(telefone), LocalDateTime.now());
    }

    /** Atendimentos de amanhã que ainda não foram confirmados pela cliente. */
    public List<Agendamento> pendentesDeConfirmacao() {
        LocalDate amanha = LocalDate.now().plusDays(1);
        return agendamentos.findByStatusAndInicioBetweenOrderByInicio(StatusAgendamento.PENDENTE,
                LocalDateTime.now(), amanha.plusDays(1).atStartOfDay());
    }

    /** Horários de início livres no dia informado, para a colocação ou a manutenção do serviço. */
    public List<LocalTime> horariosDisponiveis(LocalDate dia, Long servicoId, boolean manutencao) {
        Servico servico = buscarServico(servicoId);
        exigirManutencao(servico, manutencao);
        List<LocalTime> livres = new ArrayList<>();
        StudioProperties.Expediente expediente = studio.expediente(dia.getDayOfWeek());
        if (expediente == null) return livres;

        int duracao = servico.duracao(manutencao);
        List<Agendamento> ocupados = doDia(dia).stream().filter(Agendamento::ocupaHorario).toList();
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime fechamento = dia.atTime(expediente.fechamento());
        for (LocalDateTime inicio = dia.atTime(expediente.abertura());
             !inicio.plusMinutes(duracao).isAfter(fechamento);
             inicio = inicio.plusMinutes(studio.intervaloMinutos())) {
            LocalDateTime ini = inicio, fim = inicio.plusMinutes(duracao);
            if (ini.isBefore(agora)) continue;
            boolean conflita = ocupados.stream().anyMatch(a -> ini.isBefore(a.fim) && fim.isAfter(a.inicio));
            if (!conflita) livres.add(ini.toLocalTime());
        }
        return livres;
    }

    @Transactional
    public Agendamento agendar(NovoAgendamentoRequest req, OrigemAgendamento origem) {
        Servico servico = buscarServico(req.servicoId());
        boolean manutencao = req.ehManutencao();
        LocalDateTime inicio = req.inicio();
        if (inicio == null) throw new NegocioException("Informe o dia e o horário.");
        if (!horariosDisponiveis(inicio.toLocalDate(), servico.id, manutencao).contains(inicio.toLocalTime())) {
            throw new NegocioException("O horário " + inicio.toLocalTime() + " de " + inicio.toLocalDate()
                    + " não está disponível para " + servico.nome + ".");
        }
        Agendamento a = new Agendamento();
        a.cliente = clientePorTelefone(req.nome(), req.telefone());
        a.servico = servico;
        a.manutencao = manutencao;
        a.inicio = inicio;
        a.fim = inicio.plusMinutes(servico.duracao(manutencao));
        a.valor = servico.preco(manutencao);
        // o sinal é sobre o preço da técnica, sem a taxa de deslocamento
        if (!manutencao) a.valorSinal = studio.sinal(servico.preco);
        a.observacoes = req.observacoes();
        a.origem = origem;
        if (req.ehDomicilio()) {
            if (req.endereco() == null || req.endereco().isBlank()) {
                throw new NegocioException("Para atendimento a domicílio, informe o endereço da cliente.");
            }
            String cep = req.cep() == null ? "" : req.cep().replaceAll("\\D", "");
            a.domicilio = true;
            a.enderecoAtendimento = req.endereco().trim();
            a.cepAtendimento = cep.length() == 8 ? cep.substring(0, 5) + "-" + cep.substring(5) : null;
            calcularDeslocamento(a);
            a.cliente.endereco = a.enderecoAtendimento + (a.cepAtendimento == null ? "" : " - CEP " + a.cepAtendimento);
        }
        return agendamentos.save(a);
    }

    /**
     * Calcula a distância a partir do ponto de saída do dia marcado e soma a taxa ao preço da técnica.
     * Se o endereço não for achado no mapa, fica sem taxa e a profissional combina com a cliente.
     */
    private void calcularDeslocamento(Agendamento a) {
        a.valor = a.servico.preco(a.manutencao);
        a.distancias = null;
        a.taxaDeslocamento = null;
        distancia.calcular(a.cepAtendimento, a.enderecoAtendimento).ifPresent(d -> {
            DistanciaService.Trajeto saida = d.saidaNo(a.inicio.getDayOfWeek());
            a.distancias = d.resumo() + " · taxa calculada saindo de " + saida.origem();
            a.taxaDeslocamento = studio.taxaDeslocamento(saida.km());
            a.valor = a.valor.add(a.taxaDeslocamento);
        });
    }

    @Transactional
    public Agendamento remarcar(Long id, LocalDateTime novoInicio) {
        Agendamento a = buscar(id);
        exigirAtivo(a);
        StatusAgendamento statusAnterior = a.status;
        a.status = StatusAgendamento.CANCELADO; // libera o horário atual para a checagem
        agendamentos.saveAndFlush(a);
        if (!horariosDisponiveis(novoInicio.toLocalDate(), a.servico.id, a.manutencao).contains(novoInicio.toLocalTime())) {
            a.status = statusAnterior;
            agendamentos.save(a);
            throw new NegocioException("O horário " + novoInicio.toLocalTime() + " de " + novoInicio.toLocalDate()
                    + " não está disponível.");
        }
        a.inicio = novoInicio;
        a.fim = novoInicio.plusMinutes(a.servico.duracao(a.manutencao));
        // mudar de dia de semana para fim de semana (ou o contrário) muda o ponto de saída e a taxa
        if (a.domicilio) calcularDeslocamento(a);
        a.status = StatusAgendamento.PENDENTE;
        return agendamentos.save(a);
    }

    @Transactional
    public Agendamento confirmar(Long id) {
        Agendamento a = buscar(id);
        exigirAtivo(a);
        a.status = StatusAgendamento.CONFIRMADO;
        return agendamentos.save(a);
    }

    @Transactional
    public Agendamento registrarSinal(Long id) {
        Agendamento a = buscar(id);
        exigirAtivo(a);
        if (a.valorSinal == null) throw new NegocioException("Este agendamento não tem sinal.");
        a.sinalPago = true;
        return agendamentos.save(a);
    }

    @Transactional
    public Agendamento cancelar(Long id) {
        Agendamento a = buscar(id);
        exigirAtivo(a);
        a.status = StatusAgendamento.CANCELADO;
        return agendamentos.save(a);
    }

    @Transactional
    public Agendamento marcarFalta(Long id) {
        Agendamento a = buscar(id);
        a.status = StatusAgendamento.FALTOU;
        return agendamentos.save(a);
    }

    /** Marca como realizado e lança o valor recebido no financeiro. */
    @Transactional
    public Agendamento concluir(Long id, java.math.BigDecimal valorRecebido, String formaPagamento) {
        Agendamento a = buscar(id);
        if (a.status == StatusAgendamento.CANCELADO) throw new NegocioException("Este agendamento foi cancelado.");
        a.status = StatusAgendamento.REALIZADO;
        if (valorRecebido != null) a.valor = valorRecebido;
        financeiro.registrarAtendimento(a, formaPagamento);
        return agendamentos.save(a);
    }

    public Agendamento buscar(Long id) {
        return agendamentos.findById(id).orElseThrow(() -> new NegocioException("Agendamento " + id + " não encontrado."));
    }

    private Servico buscarServico(Long id) {
        return servicos.findById(id).filter(s -> s.ativo)
                .orElseThrow(() -> new NegocioException("Serviço " + id + " não encontrado."));
    }

    private static void exigirManutencao(Servico servico, boolean manutencao) {
        if (manutencao && !servico.temManutencao()) {
            throw new NegocioException(servico.nome + " não tem manutenção cadastrada.");
        }
    }

    private void exigirAtivo(Agendamento a) {
        if (a.status != StatusAgendamento.PENDENTE && a.status != StatusAgendamento.CONFIRMADO) {
            throw new NegocioException("Este agendamento está " + a.status.name().toLowerCase() + " e não pode ser alterado.");
        }
    }

    private Cliente clientePorTelefone(String nome, String telefone) {
        String tel = Cliente.normalizarTelefone(telefone);
        if (tel == null || tel.length() < 10) throw new NegocioException("Informe um telefone válido com DDD.");
        return clientes.findByTelefone(tel).orElseGet(() -> {
            if (nome == null || nome.isBlank()) throw new NegocioException("Informe o nome da cliente.");
            Cliente c = new Cliente();
            c.nome = nome.trim();
            c.telefone = tel;
            return clientes.save(c);
        });
    }
}
