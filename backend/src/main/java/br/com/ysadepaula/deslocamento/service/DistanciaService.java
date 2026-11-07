package br.com.ysadepaula.deslocamento.service;

import br.com.ysadepaula.compartilhado.config.StudioProperties;
import br.com.ysadepaula.compartilhado.config.StudioProperties.PontoPartida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Calcula a distância dos pontos de partida da profissional até o endereço da cliente, para o atendimento a
 * domicílio. Usa três serviços gratuitos e sem chave:
 * <ol>
 *     <li>ViaCEP: transforma o CEP em rua, bairro e cidade;</li>
 *     <li>Nominatim (OpenStreetMap): transforma o endereço em latitude e longitude;</li>
 *     <li>OSRM: calcula o caminho de carro e o tempo.</li>
 * </ol>
 * Se a rota falhar, cai para a distância em linha reta. Nada aqui impede o agendamento: sem internet, o
 * agendamento é salvo sem a distância.
 */
@Service
public class DistanciaService {

    private static final Logger log = LoggerFactory.getLogger(DistanciaService.class);
    private static final double RAIO_TERRA_KM = 6371;

    public record Coordenada(double latitude, double longitude) {
    }

    public record Trajeto(String origem, double km, Long minutos) {

        /** Ex.: "Studio: 5,2 km (~12 min)" ou "Studio: ~4,1 km em linha reta". */
        public String descricao() {
            String km = String.format(Locale.of("pt", "BR"), "%.1f", this.km);
            return minutos == null
                    ? origem + ": ~" + km + " km em linha reta"
                    : origem + ": " + km + " km (~" + minutos + " min de carro)";
        }
    }

    private final StudioProperties studio;
    private final RestClient http;

    public DistanciaService(StudioProperties studio, RestClient.Builder builder) {
        this.studio = studio;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        // o Nominatim exige que cada sistema se identifique no User-Agent
        this.http = builder.requestFactory(timeouts)
                .defaultHeader("User-Agent", "YsaDePaulaStudio/1.0 (" + studio.urlPublica() + ")")
                .build();
    }

    /** Distâncias de cada ponto de partida até o endereço, em texto, ou null se não deu para localizar. */
    public String resumo(String cep, String endereco) {
        return localizar(cep, endereco)
                .map(destino -> trajetos(destino).stream().map(Trajeto::descricao).collect(Collectors.joining(" · ")))
                .orElse(null);
    }

    public List<Trajeto> trajetos(Coordenada destino) {
        List<PontoPartida> pontos = studio.pontosPartida() == null ? List.of() : studio.pontosPartida();
        return pontos.stream().map(p -> {
            Coordenada origem = new Coordenada(p.latitude(), p.longitude());
            return rota(origem, destino)
                    .map(r -> new Trajeto(p.nome(), r.km(), r.minutos()))
                    .orElseGet(() -> new Trajeto(p.nome(), linhaReta(origem, destino), null));
        }).toList();
    }

    /** Tenta do mais preciso para o menos preciso: endereço completo, depois rua e bairro, depois só o bairro. */
    Optional<Coordenada> localizar(String cep, String endereco) {
        Map<String, Object> viaCep = buscarCep(cep);
        String bairro = texto(viaCep.get("bairro"));
        String cidade = texto(viaCep.get("localidade"));
        String uf = texto(viaCep.get("uf"));
        String rua = texto(viaCep.get("logradouro"));

        List<String> buscas = new ArrayList<>();
        String regiao = cidade.isEmpty() ? "" : ", " + cidade + ", " + uf;
        if (endereco != null && !endereco.isBlank()) buscas.add(endereco + regiao + ", Brasil");
        if (!rua.isEmpty()) buscas.add(rua + ", " + bairro + regiao + ", Brasil");
        if (!bairro.isEmpty()) buscas.add(bairro + regiao + ", Brasil");

        for (int i = 0; i < buscas.size(); i++) {
            if (i > 0) esperar(1100); // o Nominatim aceita no máximo uma busca por segundo
            Optional<Coordenada> achou = geocodificar(buscas.get(i));
            if (achou.isPresent()) return achou;
        }
        log.warn("Não foi possível localizar o endereço {} (CEP {})", endereco, cep);
        return Optional.empty();
    }

    private Map<String, Object> buscarCep(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8) return Map.of();
        try {
            Map<String, Object> r = http.get().uri("https://viacep.com.br/ws/{cep}/json/", digitos).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return r == null || r.containsKey("erro") ? Map.of() : r;
        } catch (RestClientException e) {
            log.warn("ViaCEP indisponível: {}", e.getMessage());
            return Map.of();
        }
    }

    private Optional<Coordenada> geocodificar(String busca) {
        try {
            List<Map<String, Object>> r = http.get()
                    .uri("https://nominatim.openstreetmap.org/search?q={q}&countrycodes=br&format=jsonv2&limit=1", busca)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            if (r == null || r.isEmpty()) return Optional.empty();
            return Optional.of(new Coordenada(Double.parseDouble(texto(r.getFirst().get("lat"))),
                    Double.parseDouble(texto(r.getFirst().get("lon")))));
        } catch (RestClientException | NumberFormatException e) {
            log.warn("Nominatim indisponível: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private record Rota(double km, long minutos) {
    }

    @SuppressWarnings("unchecked")
    private Optional<Rota> rota(Coordenada de, Coordenada ate) {
        try {
            // o OSRM recebe longitude antes da latitude
            Map<String, Object> r = http.get()
                    .uri(String.format(Locale.ROOT, "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=false",
                            de.longitude(), de.latitude(), ate.longitude(), ate.latitude()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (r == null || !(r.get("routes") instanceof List<?> rotas) || rotas.isEmpty()) return Optional.empty();
            Map<String, Object> melhor = (Map<String, Object>) rotas.getFirst();
            double metros = ((Number) melhor.get("distance")).doubleValue();
            double segundos = ((Number) melhor.get("duration")).doubleValue();
            return Optional.of(new Rota(metros / 1000, Math.round(segundos / 60)));
        } catch (RestClientException | ClassCastException e) {
            log.warn("OSRM indisponível: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Fórmula de haversine: distância em km entre dois pontos na superfície da Terra. */
    public static double linhaReta(Coordenada a, Coordenada b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.sqrt(h));
    }

    private static String texto(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
