package br.com.ysadepaula.compartilhado.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conta quantas vezes algo aconteceu para uma chave (o IP, por exemplo) dentro de uma janela de tempo.
 * Fica em memória: ao reiniciar o sistema, as contagens zeram.
 */
public class LimitadorTentativas {

    private final int limite;
    private final Duration janela;
    private final Clock relogio;
    private final Map<String, Deque<Instant>> registros = new ConcurrentHashMap<>();

    public LimitadorTentativas(int limite, Duration janela) {
        this(limite, janela, Clock.systemUTC());
    }

    LimitadorTentativas(int limite, Duration janela, Clock relogio) {
        this.limite = limite;
        this.janela = janela;
        this.relogio = relogio;
    }

    /** Registra mais uma ocorrência e diz se a chave passou do limite. */
    public boolean registrarEExcedeu(String chave) {
        Instant agora = relogio.instant();
        if (registros.size() > 10_000) limparAntigos(agora);
        Deque<Instant> lista = registros.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (lista) {
            descartarAntigos(lista, agora);
            lista.addLast(agora);
            return lista.size() > limite;
        }
    }

    /** Diz se a chave já atingiu o limite, sem registrar nada. */
    public boolean atingiuLimite(String chave) {
        Deque<Instant> lista = registros.get(chave);
        if (lista == null) return false;
        synchronized (lista) {
            descartarAntigos(lista, relogio.instant());
            return lista.size() >= limite;
        }
    }

    public void zerar(String chave) {
        registros.remove(chave);
    }

    private void descartarAntigos(Deque<Instant> lista, Instant agora) {
        Instant corte = agora.minus(janela);
        while (!lista.isEmpty() && !lista.peekFirst().isAfter(corte)) lista.pollFirst();
    }

    private void limparAntigos(Instant agora) {
        registros.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                descartarAntigos(e.getValue(), agora);
                return e.getValue().isEmpty();
            }
        });
    }
}
