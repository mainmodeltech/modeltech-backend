package com.modeltech.datamasteryhub.security;

import com.modeltech.datamasteryhub.modules.auth.permission.AdminAccessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration Spring Security — JWT stateless.
 *
 * Routes publiques :
 *   POST /api/v1/auth/login
 *   POST /api/v1/auth/forgot-password
 *   POST /api/v1/auth/reset-password
 *   GET  /swagger-ui/**
 *   GET  /v3/api-docs/**
 *
 * Toutes les autres routes nécessitent un JWT valide.
 * @EnableMethodSecurity active @PreAuthorize pour le RBAC granulaire.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService      userDetailsService;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5000}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Non authentifié (pas de jeton, jeton expiré ou révoqué) → 401 ; un utilisateur connecté
                // sans le bon rôle reçoit 403 (AccessDenied).
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    String path = request.getRequestURI().replace("\\", "").replace("\"", "");
                    response.getWriter().write("{\"timestamp\":\"" + java.time.LocalDateTime.now()
                            + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentification requise ou session expirée.\",\"path\":\""
                            + path + "\"}");
                }))
                .authorizeHttpRequests(auth -> {
                    auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // ── Routes publiques ────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/passwordless/request",
                                "/api/v1/auth/passwordless/verify",
                                "/api/v1/auth/google",
                                // Session par cookie HttpOnly (origine controlee par le controleur) et seconde etape du personnel
                                // (defi MFA signe porte par le corps de la requete)
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/mfa/enroll/start",
                                "/api/v1/auth/mfa/enroll/confirm",
                                "/api/v1/masterclass/register",
                                "/api/v1/contact-messages",
                                "/api/v1/diagnostic-requests",
                                "/api/v1/partner-applications",
                                "/api/v1/newsletter/subscriptions",
                                "/api/v1/newsletter/subscriptions/confirm",
                                "/api/v1/newsletter/subscriptions/unsubscribe",
                                "/api/v1/registrations",
                                // Lien de paiement : le jeton (256 bits, expirant) fait office d'authentification
                                "/api/v1/payments/*/declaration",
                                "/api/v1/payments/*/proof"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/options",
                                "/api/v1/masterclass/upcoming",
                                "/api/v1/masterclass/*",
                                "/api/v1/bootcamps",
                                "/api/v1/bootcamps/**",
                                "/api/v1/formations",
                                "/api/v1/formations/**",
                                "/api/v1/domains",
                                "/api/v1/partners",
                                "/api/v1/services",
                                "/api/v1/services/**",
                                "/api/v1/promo-codes/validate",
                                "/api/v1/testimonials/published",
                                "/api/v1/payments/*",
                                "/api/v1/payments/*/invoice",
                                "/api/v1/payments/*/receipt",
                                "/api/v1/certificates/*",
                                "/api/v1/certificates/*/pdf",
                                "/api/v1/site-settings",
                                "/api/v1/sessions/**",
                                "/api/v1/alumni",
                                "/api/v1/projects",
                                "/api/v1/projects/**"
                        ).permitAll()
                        // ── Swagger (dev uniquement — à restreindre en prod) ───────
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        ;
                // ── Back-office : contrôle par DROIT (table unique AdminAccessRules, refus par défaut) ──
                AdminAccessRules.apply(auth);
                auth
                        // ── Espace apprenant ──────────────────────────────────────
                        .requestMatchers("/api/v1/learner/**")
                                .hasRole("LEARNER")
                        // ── Toutes les autres routes → authentification requise ─────
                        .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * RestTemplate partagé — utilisé par RecaptchaService et MasterclassServiceImpl
     * (Slack notifications).
     */
    @Bean
    public RestTemplate restTemplate() {
        // Délais bornés : un service tiers lent (Google, Slack) ne doit pas bloquer un fil de requête indéfiniment
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}