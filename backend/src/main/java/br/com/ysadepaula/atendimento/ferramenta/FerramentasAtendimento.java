package br.com.ysadepaula.atendimento.ferramenta;

import br.com.ysadepaula.agenda.dto.NovoAgendamentoRequest;
import br.com.ysadepaula.agenda.model.Agendamento;
import br.com.ysadepaula.agenda.model.OrigemAgendamento;
import br.com.ysadepaula.agenda.service.AgendaService;
import br.com.ysadepaula.cliente.model.Cliente;
import br.com.ysadepaula.compartilhado.exception.NegocioException;
import br.com.ysadepaula.servico.model.Servico;
import br.com.ysadepaula.servico.repository.ServicoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.ysadepaula.compartilhado.util.Datas.diaSemana;

/**
 * Ferramentas que a atendente virtual pode usar. Cada ferramenta conversa com os serviços do sistema,
 * então a IA nunca inventa horário, preço ou status: ela sempre consulta o banco de dados.
 */
@Component
public class FerramentasAtendimento {

    private final AgendaService agenda;
    private final ServicoRepository servicos;

    public FerramentasAtendimento(AgendaService agenda, ServicoRepository servicos) {
        this.agenda = agenda;
        this.servicos = servicos;
    }

    public List<Map<String, Object>> definicoes() {
        Map<String, Object> telefone = prop("string", "Telefone da cliente com DDD, só números. Ex.: 11987654321");
        Map<String, Object> agendamentoId = prop("integer", "ID do agendamento, obtido em buscar_agendamentos");
        Map<String, Object> tipo = Map.of("type", "string", "enum", List.of("colocacao", "manutencao"),
                "description", "colocacao (aplicação nova) ou manutencao (reposição dos fios)");
        return List.of(
                tool("listar_servicos",
                        "Lista as técnicas do studio com ID, descrição, duração e preço da colocação e da manutenção. "
                                + "Use antes de falar de preços ou de agendar.",
                        Map.of(), List.of()),
                tool("consultar_horarios",
                        "Retorna os horários livres de um dia para uma técnica. Use sempre antes de oferecer horários.",
                        Map.of("data", prop("string", "Dia no formato AAAA-MM-DD"),
                                "servico_id", prop("integer", "ID do serviço"),
                                "tipo", tipo),
                        List.of("data", "servico_id", "tipo")),
                tool("agendar",
                        "Cria o agendamento. Só use depois que a cliente concordar com técnica, tipo, local, dia e "
                                + "horário e informar nome e telefone (e o endereço, se for em casa).",
                        Map.of("nome", prop("string", "Nome completo da cliente"),
                                "telefone", telefone,
                                "servico_id", prop("integer", "ID do serviço"),
                                "tipo", tipo,
                                "data", prop("string", "Dia no formato AAAA-MM-DD"),
                                "horario", prop("string", "Horário de início no formato HH:mm"),
                                "domicilio", prop("boolean", "true se a cliente quer ser atendida em casa"),
                                "endereco", prop("string", "Endereço completo da cliente (obrigatório se domicilio for true)"),
                                "cep", prop("string", "CEP da cliente, só números (se domicilio for true)"),
                                "observacoes", prop("string", "Observações úteis para a profissional (opcional)")),
                        List.of("nome", "telefone", "servico_id", "tipo", "data", "horario")),
                tool("buscar_agendamentos",
                        "Lista os próximos agendamentos da cliente pelo telefone.",
                        Map.of("telefone", telefone), List.of("telefone")),
                tool("confirmar_agendamento",
                        "Confirma a presença da cliente em um agendamento.",
                        Map.of("agendamento_id", agendamentoId, "telefone", telefone),
                        List.of("agendamento_id", "telefone")),
                tool("remarcar_agendamento",
                        "Muda o dia/horário de um agendamento. Consulte os horários livres antes.",
                        Map.of("agendamento_id", agendamentoId, "telefone", telefone,
                                "data", prop("string", "Novo dia no formato AAAA-MM-DD"),
                                "horario", prop("string", "Novo horário no formato HH:mm")),
                        List.of("agendamento_id", "telefone", "data", "horario")),
                tool("cancelar_agendamento",
                        "Cancela um agendamento. Só use depois que a cliente confirmar que quer cancelar.",
                        Map.of("agendamento_id", agendamentoId, "telefone", telefone),
                        List.of("agendamento_id", "telefone")));
    }

    /** Executa a ferramenta e devolve o resultado (que será convertido em JSON para a IA). */
    public Object executar(String nome, Map<String, Object> in) {
        return switch (nome) {
            case "listar_servicos" -> servicos.findByAtivoTrueOrderByNome().stream().map(this::resumo).toList();
            case "consultar_horarios" -> {
                LocalDate dia = LocalDate.parse(texto(in, "data"));
                List<String> livres = agenda.horariosDisponiveis(dia, numero(in, "servico_id"), manutencao(in)).stream()
                        .map(LocalTime::toString).toList();
                yield Map.of("data", dia.toString(), "dia_semana", diaSemana(dia),
                        "horarios_livres", livres,
                        "observacao", livres.isEmpty() ? "Sem horários livres (ou o studio não abre neste dia)." : "");
            }
            case "agendar" -> resumo(agenda.agendar(new NovoAgendamentoRequest(texto(in, "nome"), texto(in, "telefone"),
                    numero(in, "servico_id"), dataHora(in), opcional(in, "observacoes"), manutencao(in),
                    Boolean.parseBoolean(String.valueOf(in.get("domicilio"))), opcional(in, "cep"),
                    opcional(in, "endereco")), OrigemAgendamento.IA));
            case "buscar_agendamentos" -> agenda.proximosDaCliente(texto(in, "telefone")).stream()
                    .filter(Agendamento::ocupaHorario).map(this::resumo).toList();
            case "confirmar_agendamento" -> resumo(agenda.confirmar(daCliente(in).id));
            case "remarcar_agendamento" -> resumo(agenda.remarcar(daCliente(in).id, dataHora(in)));
            case "cancelar_agendamento" -> resumo(agenda.cancelar(daCliente(in).id));
            default -> throw new NegocioException("Ferramenta desconhecida: " + nome);
        };
    }

    /** Garante que a cliente só mexe nos próprios agendamentos. */
    private Agendamento daCliente(Map<String, Object> in) {
        Agendamento a = agenda.buscar(numero(in, "agendamento_id"));
        if (!a.cliente.telefone.equals(Cliente.normalizarTelefone(texto(in, "telefone")))) {
            throw new NegocioException("Este agendamento não pertence ao telefone informado.");
        }
        return a;
    }

    private Map<String, Object> resumo(Servico s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("nome", s.nome);
        m.put("descricao", s.descricao);
        m.put("colocacao", "R$ " + reais(s.preco) + " (" + s.duracaoMinutos + " min)");
        m.put("manutencao", s.temManutencao()
                ? "R$ " + reais(s.precoManutencao) + " (" + s.duracaoManutencaoMinutos + " min)"
                : "não tem manutenção");
        return m;
    }

    private static String reais(java.math.BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    private Map<String, Object> resumo(Agendamento a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agendamento_id", a.id);
        m.put("cliente", a.cliente.nome);
        m.put("servico", a.servico.nome);
        m.put("tipo", a.manutencao ? "manutenção" : "colocação");
        m.put("valor", "R$ " + reais(a.valor));
        m.put("local", a.domicilio ? "na casa da cliente: " + a.enderecoAtendimento : "no studio");
        m.put("data", a.inicio.toLocalDate().toString());
        m.put("dia_semana", diaSemana(a.inicio.toLocalDate()));
        m.put("horario", a.inicio.toLocalTime().toString());
        m.put("termino_previsto", a.fim.toLocalTime().toString());
        m.put("status", a.status.name());
        return m;
    }

    private static LocalDateTime dataHora(Map<String, Object> in) {
        return LocalDate.parse(texto(in, "data")).atTime(LocalTime.parse(texto(in, "horario")));
    }

    private static String texto(Map<String, Object> in, String campo) {
        Object v = in.get(campo);
        if (v == null || v.toString().isBlank()) throw new NegocioException("Campo obrigatório ausente: " + campo);
        return v.toString();
    }

    private static String opcional(Map<String, Object> in, String campo) {
        Object v = in.get(campo);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    private static boolean manutencao(Map<String, Object> in) {
        return "manutencao".equalsIgnoreCase(String.valueOf(in.get("tipo")).replace("ç", "c").replace("ã", "a"));
    }

    private static Long numero(Map<String, Object> in, String campo) {
        Object v = in.get(campo);
        if (v instanceof Number n) return n.longValue();
        return Long.valueOf(texto(in, campo));
    }

    private static Map<String, Object> prop(String tipo, String descricao) {
        return Map.of("type", tipo, "description", descricao);
    }

    /** Definição no formato de function calling das APIs compatíveis com a OpenAI. */
    private static Map<String, Object> tool(String nome, String descricao, Map<String, Object> props,
                                            List<String> obrigatorios) {
        Map<String, Object> funcao = new LinkedHashMap<>();
        funcao.put("name", nome);
        funcao.put("description", descricao);
        // ferramenta sem parâmetros vai sem schema: alguns provedores recusam um objeto sem propriedades
        if (!props.isEmpty()) {
            funcao.put("parameters", Map.of("type", "object", "properties", props, "required", obrigatorios));
        }
        return Map.of("type", "function", "function", funcao);
    }
}
