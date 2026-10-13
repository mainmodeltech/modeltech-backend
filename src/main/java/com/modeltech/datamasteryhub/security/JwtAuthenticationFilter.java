package com.modeltech.datamasteryhub.security;

import com.modeltech.datamasteryhub.modules.auth.repository.TokenBlacklistRepository;
import com.modeltech.datamasteryhub.modules.auth.service.MfaPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Filtre JWT — exécuté une seule fois par requête.
 *
 * Pipeline :
 *  1. Extraire le Bearer token du header Authorization
 *  2. Valider la signature + expiration et exiger un jeton d'accès (un défi MFA ne vaut pas authentification)
 *  3. Vérifier que le token n'est pas en blacklist (révocation logout)
 *  4. Charger le compte (actif) et vérifier que sa version de sécurité est celle du jeton : un changement de
 *     mot de passe, de rôle, une désactivation ou une réinitialisation du MFA invalide tous les jetons déjà émis
 *  5. Personnel : exiger que le second facteur ait été validé (claim {@code mfa})
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider         jwtTokenProvider;
    private final UserDetailsService       userDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final MfaPolicy                mfaPolicy;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            Optional<JwtTokenProvider.TokenClaims> claims = jwtTokenProvider.parseOfType(token, JwtTokenProvider.TYPE_ACCESS);

            if (claims.isPresent()) {
                // Vérifier la blacklist (token révoqué par logout)
                if (isBlacklisted(token)) {
                    log.debug("Token révoqué détecté — accès refusé");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token révoqué");
                    return;
                }

                AppUserDetails user = loadActiveUser(claims.get().email());

                // Compte supprimé, désactivé, modifié depuis l'émission du jeton, ou second facteur non validé :
                // la requête reste anonyme (donc refusée sur les routes protégées) au lieu de planter ou de passer.
                if (user != null
                        && user.securityVersion() == claims.get().securityVersion()
                        && (!mfaPolicy.enforcedFor(user) || (claims.get().mfaVerified() && user.mfaEnabled()))) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** @return l'utilisateur s'il existe et est actif, sinon null. */
    private AppUserDetails loadActiveUser(String email) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (userDetails instanceof AppUserDetails user && user.isEnabled() && user.isAccountNonLocked()) return user;
            return null;
        } catch (UsernameNotFoundException e) {
            log.debug("Jeton valide pour un compte inexistant");
            return null;
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isBlacklisted(String token) {
        try {
            String hash = sha256(token);
            return tokenBlacklistRepository.existsByTokenHash(hash);
        } catch (Exception e) {
            log.warn("Erreur lors de la vérification de la blacklist: {}", e.getMessage());
            return false;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
