package br.com.ysadepaula.compartilhado.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O chat da cliente é público; o painel (/admin) e a API administrativa exigem login.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/chat.html", "/login.html", "/css/**", "/img/**",
                                "/js/chat.js", "/js/site.js", "/js/login.js", "/favicon.svg", "/api/chat/**",
                                "/api/publico/**", "/api/csrf", "/error").permitAll()
                        .anyRequest().authenticated())
                // o chat público não usa sessão de login, então não precisa de token CSRF
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat/**"))
                .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/admin/", true).failureUrl("/login.html?erro").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login.html?saiu").permitAll());
        return http.build();
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
