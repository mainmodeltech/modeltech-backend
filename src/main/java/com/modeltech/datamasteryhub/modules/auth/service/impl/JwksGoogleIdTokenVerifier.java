package com.modeltech.datamasteryhub.modules.auth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.modules.auth.service.GoogleIdTokenVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Vérification locale de la signature RS256 avec les clés publiques de Google (JWKS, mises en cache).
 * Contrôles : signature, expiration, audience = notre client, émetteur Google.
 */
@Component
@Slf4j
public class JwksGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration CACHE = Duration.ofHours(1);

    private final String clientId;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private volatile Map<String, Key> keys = Map.of();
    private volatile Instant keysFetchedAt = Instant.EPOCH;

    public JwksGoogleIdTokenVerifier(@Value("${app.auth.google.client-id:}") String clientId, ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.objectMapper = objectMapper;
    }

    @Override
    public String clientId() {
        return clientId;
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        if (!enabled() || idToken == null || idToken.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            return keyFor(header.getKeyId());
                        }
                    })
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(idToken.trim())
                    .getPayload();
            if (!ISSUERS.contains(claims.getIssuer())) return Optional.empty();
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) return Optional.empty();
            Object verified = claims.get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));
            return Optional.of(new GoogleIdentity(email, emailVerified, claims.get("name", String.class)));
        } catch (Exception e) {
            log.info("Jeton Google refusé : {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Key keyFor(String kid) {
        Key key = keys.get(kid);
        // clé inconnue (rotation) ou cache périmé : un rechargement, pas plus d'un par minute
        if (key == null && Duration.between(keysFetchedAt, Instant.now()).compareTo(Duration.ofMinutes(1)) > 0
                || Duration.between(keysFetchedAt, Instant.now()).compareTo(CACHE) > 0) {
            refreshKeys();
            key = keys.get(kid);
        }
        if (key == null) throw new IllegalStateException("Clé Google inconnue : " + kid);
        return key;
    }

    private synchronized void refreshKeys() {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(JWKS_URL))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
            Map<String, Key> fresh = new HashMap<>();
            KeyFactory factory = KeyFactory.getInstance("RSA");
            for (JsonNode jwk : objectMapper.readTree(response.body()).path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asText())) continue;
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
                fresh.put(jwk.path("kid").asText(), factory.generatePublic(new RSAPublicKeySpec(n, e)));
            }
            keys = fresh;
            keysFetchedAt = Instant.now();
        } catch (Exception e) {
            keysFetchedAt = Instant.now();   // évite de marteler Google en cas de panne
            throw new IllegalStateException("Impossible de charger les clés Google : " + e.getMessage(), e);
        }
    }
}
