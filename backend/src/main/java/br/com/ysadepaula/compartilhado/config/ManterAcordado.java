package br.com.ysadepaula.compartilhado.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * O plano grátis do Render desliga o site depois de 15 minutos sem visitas, e a próxima cliente espera
 * uns 30 segundos até ele ligar de novo. Esta tarefa visita o próprio endereço público a cada 10 minutos:
 * a visita passa pela internet e pelo Render como a de qualquer pessoa, então conta como movimento.
 * <p>
 * Limite: se o site chegar a dormir (ex.: o Render reiniciou a máquina), ele só volta com uma visita de fora.
 */
@Component
public class ManterAcordado {

    private static final Logger log = LoggerFactory.getLogger(ManterAcordado.class);

    private final StudioProperties studio;
    private final RestClient http;

    public ManterAcordado(StudioProperties studio, RestClient.Builder builder) {
        this.studio = studio;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(30));
        this.http = builder.requestFactory(timeouts).build();
    }

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = 10 * 60 * 1000)
    void visitarOProprioSite() {
        // o Render informa o endereço público do site nesta variável; fora dele, usa o studio.url-publica
        String render = System.getenv("RENDER_EXTERNAL_URL");
        String url = render != null && !render.isBlank() ? render : studio.urlPublica();
        // no computador (localhost) não tem por que manter acordado
        if (url == null || url.isBlank() || url.contains("localhost") || url.contains("127.0.0.1")) return;
        try {
            http.get().uri(url + "/api/publico/studio").retrieve().toBodilessEntity();
            log.debug("Visita para manter o site acordado: ok");
        } catch (RestClientException e) {
            log.warn("Não consegui visitar {} para manter o site acordado: {}", url, e.getMessage());
        }
    }
}
