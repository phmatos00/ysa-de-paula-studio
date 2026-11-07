package br.com.ysadepaula.atendimento.dto;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resposta da atendente para a página de chat.
 *
 * @param opcoes escolhas que a página mostra como botões clicáveis (pode ser vazia)
 */
public record RespostaChatResponse(String conversaId, String resposta, List<String> opcoes) {

    private static final int MAX_OPCOES = 12;
    private static final int MAX_TAMANHO_OPCAO = 60;

    /** A IA escreve as escolhas numa última linha "OPCOES: A | B | C" (veja o prompt em AtendenteIA). */
    private static final Pattern LINHA_OPCOES = Pattern.compile("(?imu)^[ \\t*_-]*OP[CÇ][OÕ]ES[ \\t*_]*:(.*)$");

    public RespostaChatResponse(String conversaId, String resposta) {
        this(conversaId, resposta, List.of());
    }

    /** Separa o texto da resposta da linha de opções, que vira a lista de botões. */
    public static RespostaChatResponse daIa(String conversaId, String textoDaIa) {
        if (textoDaIa == null) return new RespostaChatResponse(conversaId, "");
        Matcher m = LINHA_OPCOES.matcher(textoDaIa);
        String ultimaLinha = null;
        while (m.find()) ultimaLinha = m.group(1);
        if (ultimaLinha == null) return new RespostaChatResponse(conversaId, textoDaIa.trim());

        List<String> opcoes = Arrays.stream(ultimaLinha.split("\\|"))
                .map(o -> o.replace("*", "").trim())
                .filter(o -> !o.isEmpty())
                .map(o -> o.length() > MAX_TAMANHO_OPCAO ? o.substring(0, MAX_TAMANHO_OPCAO) : o)
                .distinct()
                .limit(MAX_OPCOES)
                .toList();
        String texto = LINHA_OPCOES.matcher(textoDaIa).replaceAll("").trim();
        return new RespostaChatResponse(conversaId, texto, opcoes);
    }
}
