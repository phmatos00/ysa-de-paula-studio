package br.com.ysadepaula.compartilhado.config;

import br.com.ysadepaula.compartilhado.util.Datas;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dados do studio e regras de funcionamento, lidos de application.yml (prefixo "studio").
 */
@ConfigurationProperties(prefix = "studio")
public record StudioProperties(
        String nome,
        String profissional,
        String especialidade,
        String endereco,
        /** Só dígitos, com DDD; vazio esconde o WhatsApp do site. */
        String whatsapp,
        /** Usuário sem o @; vazio esconde o Instagram do site. */
        String instagram,
        String urlPublica,
        /** Horário de cada dia da semana; dia que não aparece é dia fechado. */
        Map<DayOfWeek, Expediente> horarios,
        int intervaloMinutos,
        /** Lugares de onde a profissional sai para atender a domicílio (usados no cálculo de distância). */
        List<PontoPartida> pontosPartida,
        /** Taxa do atendimento a domicílio por faixa de distância; abaixo da primeira faixa não há taxa. */
        List<FaixaDeslocamento> taxasDeslocamento,
        /** Porcentagem do preço da colocação cobrada como sinal para garantir o horário (0 = sem sinal). */
        int sinalPercentual,
        /** Atraso máximo aceito; acima disso (ou se a cliente desmarcar) o sinal não é devolvido. */
        int toleranciaAtrasoMinutos,
        /** Chave Pix para o sinal; vazia = a profissional manda a chave pelo WhatsApp. */
        String chavePix,
        String adminUsuario,
        String adminSenha,
        String modeloIa,
        String modeloIaReserva,
        String iaUrl
) {

    public record Expediente(LocalTime abertura, LocalTime fechamento) {
    }

    /** Lugar de onde a profissional sai nos dias listados (sem dias = qualquer dia). */
    public record PontoPartida(String nome, double latitude, double longitude, Set<DayOfWeek> dias) {
    }

    /** A partir desta distância (em km, de carro), cobra este valor de deslocamento. */
    public record FaixaDeslocamento(double aPartirDeKm, BigDecimal valor) {
    }

    /** Valor do deslocamento para a distância informada: o da maior faixa alcançada, ou zero. */
    public BigDecimal taxaDeslocamento(double km) {
        if (taxasDeslocamento == null) return BigDecimal.ZERO;
        return taxasDeslocamento.stream()
                .filter(f -> km >= f.aPartirDeKm())
                .max(Comparator.comparingDouble(FaixaDeslocamento::aPartirDeKm))
                .map(FaixaDeslocamento::valor)
                .orElse(BigDecimal.ZERO);
    }

    /** Ex.: "até 10 km sem taxa; de 10 a 30 km, R$ 15,00; acima de 30 km, R$ 30,00". */
    public String descricaoDeslocamento() {
        if (taxasDeslocamento == null || taxasDeslocamento.isEmpty()) return "sem taxa de deslocamento";
        List<FaixaDeslocamento> faixas = taxasDeslocamento.stream()
                .sorted(Comparator.comparingDouble(FaixaDeslocamento::aPartirDeKm)).toList();
        NumberFormat reais = NumberFormat.getCurrencyInstance(Datas.PT_BR);
        NumberFormat km = NumberFormat.getNumberInstance(Datas.PT_BR);
        StringBuilder texto = new StringBuilder("até " + km.format(faixas.getFirst().aPartirDeKm()) + " km sem taxa");
        for (int i = 0; i < faixas.size(); i++) {
            FaixaDeslocamento f = faixas.get(i);
            texto.append(i + 1 < faixas.size()
                    ? "; de " + km.format(f.aPartirDeKm()) + " a " + km.format(faixas.get(i + 1).aPartirDeKm()) + " km, "
                    : "; acima de " + km.format(f.aPartirDeKm()) + " km, ");
            texto.append(reais.format(f.valor()));
        }
        // o formato de moeda do Java usa um espaço especial depois do "R$"
        return texto.toString().replace(' ', ' ');
    }

    /** Sinal da colocação: a porcentagem configurada do preço, arredondada para centavos. */
    public BigDecimal sinal(BigDecimal precoColocacao) {
        if (precoColocacao == null || sinalPercentual <= 0) return null;
        return precoColocacao.multiply(BigDecimal.valueOf(sinalPercentual))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Regra de devolução do sinal e do deslocamento, em texto, para a atendente e o site. */
    public String politicaCancelamento() {
        return "o sinal não é devolvido se a cliente desmarcar ou se atrasar mais de " + toleranciaAtrasoMinutos
                + " minutos, porque é uma segurança para o studio; já a taxa de deslocamento é devolvida se a "
                + profissional + " ainda não tiver saído para o local";
    }

    /** Expediente do dia, ou null se o studio não abre nesse dia da semana. */
    public Expediente expediente(DayOfWeek dia) {
        return horarios == null ? null : horarios.get(dia);
    }

    /** Ex.: "Segunda a sexta, das 18:40 às 21:00; sábado e domingo, das 08:00 às 21:00". */
    public String descricaoFuncionamento() {
        // junta os dias seguidos que têm o mesmo horário
        List<List<DayOfWeek>> grupos = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            Expediente e = expediente(dia);
            if (e == null) continue;
            List<DayOfWeek> ultimo = grupos.isEmpty() ? null : grupos.getLast();
            if (ultimo != null && ultimo.getLast().plus(1) == dia && Objects.equals(expediente(ultimo.getLast()), e)) {
                ultimo.add(dia);
            } else {
                grupos.add(new ArrayList<>(List.of(dia)));
            }
        }
        List<String> partes = grupos.stream().map(g -> {
            String dias = switch (g.size()) {
                case 1 -> curto(g.getFirst());
                case 2 -> curto(g.getFirst()) + " e " + curto(g.getLast());
                default -> curto(g.getFirst()) + " a " + curto(g.getLast());
            };
            Expediente e = expediente(g.getFirst());
            return dias + ", das " + e.abertura() + " às " + e.fechamento();
        }).toList();
        String texto = String.join("; ", partes);
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** "segunda-feira" vira "segunda". */
    private static String curto(DayOfWeek dia) {
        return Datas.nomeDia(dia).replace("-feira", "");
    }
}
