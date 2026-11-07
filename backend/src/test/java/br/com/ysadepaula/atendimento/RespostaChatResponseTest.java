package br.com.ysadepaula.atendimento;

import br.com.ysadepaula.atendimento.dto.RespostaChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespostaChatResponseTest {

    @Test
    void separaALinhaDeOpcoesDoTexto() {
        RespostaChatResponse r = RespostaChatResponse.daIa("c1",
                "Vai ser colocação ou manutenção? 💕\nOPCOES: Colocação | Manutenção ");
        assertEquals("Vai ser colocação ou manutenção? 💕", r.resposta());
        assertEquals(List.of("Colocação", "Manutenção"), r.opcoes());
    }

    @Test
    void aceitaAcentoMarkdownEOpcoesRepetidas() {
        RespostaChatResponse r = RespostaChatResponse.daIa("c1", "Qual horário?\n**Opções:** 18:40 | 18:40 |  | 19:10");
        assertEquals("Qual horário?", r.resposta());
        assertEquals(List.of("18:40", "19:10"), r.opcoes());
    }

    @Test
    void semLinhaDeOpcoesNaoTemBotoes() {
        RespostaChatResponse r = RespostaChatResponse.daIa("c1", "Me passa seu nome completo?");
        assertEquals("Me passa seu nome completo?", r.resposta());
        assertEquals(List.of(), r.opcoes());
    }
}
