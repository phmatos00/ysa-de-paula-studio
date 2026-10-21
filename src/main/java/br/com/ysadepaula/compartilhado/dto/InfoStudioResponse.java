package br.com.ysadepaula.compartilhado.dto;

/** Dados do studio exibidos no site público. */
public record InfoStudioResponse(String nome, String profissional, String endereco, String funcionamento) {
}
