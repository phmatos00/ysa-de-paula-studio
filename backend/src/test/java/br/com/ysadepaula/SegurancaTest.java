package br.com.ysadepaula;

import br.com.ysadepaula.atendimento.controller.ChatController;
import br.com.ysadepaula.atendimento.service.AtendenteIA;
import br.com.ysadepaula.compartilhado.seguranca.ProtecaoLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:seguranca")
@AutoConfigureMockMvc
class SegurancaTest {

    @Autowired MockMvc mvc;
    // o chat é testado sem chamar a API de IA
    @MockitoBean AtendenteIA atendente;

    @Test
    void sitePublicoAbreSemLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/publico/servicos")).andExpect(status().isOk());
    }

    @Test
    void painelRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/admin/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login.html"));
    }

    @Test
    void apiAdministrativaRespondeNaoAutorizado() throws Exception {
        mvc.perform(get("/api/agenda/confirmacoes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/financeiro")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAcessaApi() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void outroPapelNaoAcessaApi() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isForbidden());
    }

    @Test
    void alteracaoSemTokenCsrfEhRecusada() throws Exception {
        mvc.perform(post("/api/clientes").with(req -> { req.setRemoteAddr("10.0.0.9"); return req; })
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void respostasTrazemCabecalhosDeProtecao() throws Exception {
        mvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void loginCertoEntraNoPainel() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", "lash123").with(csrf())
                        .with(req -> { req.setRemoteAddr("10.0.0.1"); return req; }))
                .andExpect(redirectedUrl("/admin/"));
    }

    @Test
    void bloqueiaDepoisDeMuitasSenhasErradas() throws Exception {
        for (int i = 1; i < ProtecaoLogin.MAX_ERROS; i++) {
            mvc.perform(post("/login").param("username", "admin").param("password", "errada").with(csrf())
                            .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; }))
                    .andExpect(redirectedUrl("/login.html?erro"));
        }
        mvc.perform(post("/login").param("username", "admin").param("password", "errada").with(csrf())
                        .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; }))
                .andExpect(redirectedUrl("/login.html?bloqueado"));
        // nem a senha certa entra enquanto o IP está bloqueado
        mvc.perform(post("/login").param("username", "admin").param("password", "lash123").with(csrf())
                        .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; }))
                .andExpect(redirectedUrl("/login.html?bloqueado"));
        // outro IP continua conseguindo entrar
        mvc.perform(post("/login").param("username", "admin").param("password", "lash123").with(csrf())
                        .with(req -> { req.setRemoteAddr("10.0.0.3"); return req; }))
                .andExpect(redirectedUrl("/admin/"));
    }

    @Test
    void chatLimitaMensagensPorIp() throws Exception {
        when(atendente.responder(any(), any(), any())).thenReturn("Oi!");
        for (int i = 0; i < ChatController.MAX_MENSAGENS; i++) {
            mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\":\"oi\"}")
                    .with(req -> { req.setRemoteAddr("10.0.0.4"); return req; })).andExpect(status().isOk());
        }
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\":\"oi\"}")
                        .with(req -> { req.setRemoteAddr("10.0.0.4"); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.resposta").value(org.hamcrest.Matchers.containsString("Aguarde")));
        // outra cliente, em outro IP, continua sendo atendida
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\":\"oi\"}")
                .with(req -> { req.setRemoteAddr("10.0.0.5"); return req; })).andExpect(status().isOk());
    }
}
