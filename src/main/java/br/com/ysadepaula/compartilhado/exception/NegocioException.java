package br.com.ysadepaula.compartilhado.exception;

/** Erro de regra de negócio com mensagem pronta para mostrar ao usuário. */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
