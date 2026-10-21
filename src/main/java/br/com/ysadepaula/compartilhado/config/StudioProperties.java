package br.com.ysadepaula.compartilhado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * Dados do studio e regras de funcionamento, lidos de application.properties (prefixo "studio").
 */
@ConfigurationProperties(prefix = "studio")
public record StudioProperties(
        String nome,
        String profissional,
        String endereco,
        String urlPublica,
        LocalTime abertura,
        LocalTime fechamento,
        Set<DayOfWeek> diasFuncionamento,
        int intervaloMinutos,
        String adminUsuario,
        String adminSenha,
        String modeloIa
) {
}
