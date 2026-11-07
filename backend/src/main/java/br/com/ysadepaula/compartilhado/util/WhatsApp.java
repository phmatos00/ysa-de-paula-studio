package br.com.ysadepaula.compartilhado.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Links "clique para conversar" do WhatsApp (wa.me), que abrem a conversa já com a mensagem escrita. */
public final class WhatsApp {

    private WhatsApp() {
    }

    /** @param telefone só dígitos, com DDD e sem o 55 */
    public static String link(String telefone, String texto) {
        // o wa.me não entende o "+" que o URLEncoder usa no lugar do espaço
        return "https://wa.me/55" + telefone + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
