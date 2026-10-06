package com.modeltech.datamasteryhub.security;

import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Émission et lecture des JWT.
 *
 * <p>Claims : {@code sub} (e-mail), {@code roles} (noms stockés, ex. {@code ROLE_ADMIN} —
 * lus par le frontend pour router selon le rôle), {@code uty} (type de compte : {@code ADMIN}
 * ou {@code LEARNER}), {@code iat}, {@code exp}. Les droits réellement appliqués par le
 * backend sont toujours relus en base à chaque requête (JwtAuthenticationFilter), jamais
 * déduits du jeton : les jetons émis avant l'ajout de {@code roles} restent valides.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USER_TYPE = "uty";
    public static final String USER_TYPE_ADMIN = "ADMIN";
    public static final String USER_TYPE_LEARNER = "LEARNER";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        boolean learnerOnly = roles.contains(RoleNames.LEARNER)
                && roles.stream().noneMatch(RoleNames.STAFF::contains);
        return buildToken(userDetails.getUsername(), roles, learnerOnly ? USER_TYPE_LEARNER : USER_TYPE_ADMIN);
    }

    private String buildToken(String email, List<String> roles, String userType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_USER_TYPE, userType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Token JWT malformé");
        } catch (ExpiredJwtException ex) {
            log.error("Token JWT expiré");
        } catch (UnsupportedJwtException ex) {
            log.error("Token JWT non supporté");
        } catch (IllegalArgumentException ex) {
            log.error("Token JWT vide");
        } catch (JwtException ex) {
            log.error("Token JWT invalide : {}", ex.getMessage());
        }
        return false;
    }

    public long getExpiration() {
        return jwtExpiration;
    }
}
