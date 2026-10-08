package com.modeltech.datamasteryhub.security;

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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // ── Routes publiques ────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
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
                        // ── Back-office : contrôle par rôle (les règles les plus précises d'abord) ──
                        .requestMatchers("/api/v1/admin/users/**")
                                .hasRole("SUPER_ADMIN")
                        .requestMatchers(
                                "/api/v1/admin/registrations/**",
                                "/api/v1/admin/promo-codes/**",
                                "/api/v1/admin/learners/**",
                                "/api/v1/admin/payments/**",
                                "/api/v1/admin/enrollments/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers("/api/v1/admin/**")
                                .hasAnyRole("SUPER_ADMIN", "ADMIN", "EDITOR")
                        // ── Toutes les autres routes → authentification requise ─────
                        .anyRequest().authenticated()
                )
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
        return new RestTemplate();
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