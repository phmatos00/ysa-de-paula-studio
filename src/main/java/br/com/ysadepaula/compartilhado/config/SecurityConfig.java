package br.com.ysadepaula.compartilhado.config;

import br.com.ysadepaula.compartilhado.seguranca.ProtecaoLogin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * O site e o chat da cliente são públicos; o painel (/admin) e a API administrativa exigem login de ADMIN.
 */
@Configuration
public class SecurityConfig {

    /** Só carrega scripts do próprio sistema; estilos e fontes também do Google Fonts. */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ProtecaoLogin protecao) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/chat.html", "/login.html", "/css/**", "/img/**",
                                "/js/chat.js", "/js/site.js", "/js/login.js", "/favicon.svg", "/api/chat/**",
                                "/api/publico/**", "/api/csrf", "/error").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                // o chat público não usa sessão de login, então não precisa de token CSRF
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat/**"))
                .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/login")
                        .successHandler(protecao.aoEntrar())
                        .failureHandler(protecao.aoErrar())
                        .permitAll())
                .addFilterBefore(protecao.filtro(), UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout.logoutSuccessUrl("/login.html?saiu").permitAll())
                // sem login, a API responde 401 e as páginas levam para a tela de login
                .exceptionHandling(ex -> ex.authenticationEntryPoint(semLogin()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    private static AuthenticationEntryPoint semLogin() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> porCaminho = new LinkedHashMap<>();
        porCaminho.put(PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entrada = new DelegatingAuthenticationEntryPoint(porCaminho);
        entrada.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login.html"));
        return entrada;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(StudioProperties studio, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(studio.adminUsuario())
                .password(encoder.encode(studio.adminSenha()))
                .roles("ADMIN")
                .build());
    }
}
