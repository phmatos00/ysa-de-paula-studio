package br.com.ysadepaula.compartilhado.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Contra quem tenta adivinhar a senha: depois de {@value #MAX_ERROS} erros seguidos vindos do mesmo IP,
 * o login fica bloqueado para esse IP por {@link #BLOQUEIO}.
 */
@Component
public class ProtecaoLogin {

    public static final int MAX_ERROS = 5;
    public static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(ProtecaoLogin.class);

    private final LimitadorTentativas erros = new LimitadorTentativas(MAX_ERROS, BLOQUEIO);

    /** Barra o POST de login antes de conferir a senha quando o IP está bloqueado. */
    public OncePerRequestFilter filtro() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest req) {
                return !("POST".equals(req.getMethod()) && (req.getContextPath() + "/login").equals(req.getRequestURI()));
            }

            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                    throws ServletException, IOException {
                if (erros.atingiuLimite(req.getRemoteAddr())) {
                    resp.sendRedirect("/login.html?bloqueado");
                    return;
                }
                chain.doFilter(req, resp);
            }
        };
    }

    public AuthenticationFailureHandler aoErrar() {
        return (req, resp, e) -> {
            String ip = req.getRemoteAddr();
            erros.registrarEExcedeu(ip);
            if (erros.atingiuLimite(ip)) {
                log.warn("Login bloqueado por {} minutos para o IP {} após {} senhas erradas",
                        BLOQUEIO.toMinutes(), ip, MAX_ERROS);
                resp.sendRedirect("/login.html?bloqueado");
            } else {
                resp.sendRedirect("/login.html?erro");
            }
        };
    }

    public AuthenticationSuccessHandler aoEntrar() {
        return (req, resp, auth) -> {
            erros.zerar(req.getRemoteAddr());
            resp.sendRedirect("/admin/");
        };
    }
}
