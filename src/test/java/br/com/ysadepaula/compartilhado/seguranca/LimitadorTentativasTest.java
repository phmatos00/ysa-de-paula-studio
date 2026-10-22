package br.com.ysadepaula.compartilhado.seguranca;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LimitadorTentativasTest {

    /** Relógio que o teste avança na mão. */
    static class RelogioManual extends Clock {
        Instant agora = Instant.parse("2026-01-01T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @Test
    void liberaDeNovoQuandoAJanelaPassa() {
        RelogioManual relogio = new RelogioManual();
        LimitadorTentativas limitador = new LimitadorTentativas(3, Duration.ofMinutes(15), relogio);

        assertFalse(limitador.registrarEExcedeu("ip"));
        assertFalse(limitador.registrarEExcedeu("ip"));
        assertFalse(limitador.atingiuLimite("ip"));
        assertFalse(limitador.registrarEExcedeu("ip"));
        assertTrue(limitador.atingiuLimite("ip"));
        assertTrue(limitador.registrarEExcedeu("ip"));
        assertFalse(limitador.atingiuLimite("outro-ip"));

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(16));
        assertFalse(limitador.atingiuLimite("ip"));
    }

    @Test
    void zerarApagaAsTentativas() {
        LimitadorTentativas limitador = new LimitadorTentativas(1, Duration.ofMinutes(15));
        limitador.registrarEExcedeu("ip");
        assertTrue(limitador.atingiuLimite("ip"));
        limitador.zerar("ip");
        assertFalse(limitador.atingiuLimite("ip"));
    }
}
