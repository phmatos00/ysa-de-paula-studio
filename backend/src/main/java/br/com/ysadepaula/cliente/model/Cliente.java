package br.com.ysadepaula.cliente.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String nome;

    /** Somente dígitos, com DDD (ex.: 11987654321). */
    @Column(nullable = false, unique = true)
    public String telefone;

    @Column(length = 1000)
    public String observacoes;

    public LocalDateTime criadoEm = LocalDateTime.now();

    public static String normalizarTelefone(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");
        // remove o código do país (55) se vier junto
        if (digitos.length() > 11 && digitos.startsWith("55")) digitos = digitos.substring(2);
        return digitos;
    }
}
