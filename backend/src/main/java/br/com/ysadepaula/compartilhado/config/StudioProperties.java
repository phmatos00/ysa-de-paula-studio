package br.com.ysadepaula.compartilhado.config;

import br.com.ysadepaula.compartilhado.util.Datas;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        String adminUsuario,
        String adminSenha,
        String modeloIa,
        String modeloIaReserva,
        String iaUrl
) {

    public record Expediente(LocalTime abertura, LocalTime fechamento) {
    }

    public record PontoPartida(String nome, double latitude, double longitude) {
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
