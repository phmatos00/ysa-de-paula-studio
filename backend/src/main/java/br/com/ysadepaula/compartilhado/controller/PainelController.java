package br.com.ysadepaula.compartilhado.controller;

import br.com.ysadepaula.compartilhado.config.StudioProperties;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Informações gerais usadas pelo painel e pela tela de login. */
@RestController
@RequestMapping("/api")
public class PainelController {

    private final StudioProperties studio;

    public PainelController(StudioProperties studio) {
        this.studio = studio;
    }

    /** Token CSRF que o painel envia no cabeçalho das requisições que alteram dados. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("header", token.getHeaderName(), "parametro", token.getParameterName(), "token", token.getToken());
    }

    /** Configurações do studio, sem os dados de login. */
    @GetMapping("/studio")
    public StudioProperties studio() {
        return new StudioProperties(studio.nome(), studio.profissional(), studio.endereco(), studio.urlPublica(),
                studio.abertura(), studio.fechamento(), studio.diasFuncionamento(), studio.intervaloMinutos(),
                null, null, studio.modeloIa(), studio.modeloIaReserva(), studio.iaUrl());
    }
}
