package com.modeltech.datamasteryhub.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Émission et lecture des JWT.
 *
 * <p><b>Jeton d'accès</b> (courte durée, {@code app.jwt.expiration}, 15 min par défaut) : {@code sub} (e-mail),
 * {@code roles}, {@code uty} (ADMIN ou LEARNER), {@code sid} (session), {@code sv} (version de sécurité du
 * compte : un changement de mot de passe, de rôle, une désactivation ou une réinitialisation du MFA l'incrémente
 * et invalide tous les jetons déjà émis), {@code mfa} (le second facteur a été validé). La session se prolonge
 * par le jeton de rafraîchissement ({@code HttpOnly}, voir {@code AuthSessionService}), jamais par un jeton
 * d'accès de longue durée. Les droits réellement appliqués sont relus en base à chaque requête.
 *
 * <p><b>Jeton de défi MFA</b> (5 minutes, {@code tty=mfa}) : délivré après un premier facteur réussi pour un compte
 * du personnel ; il ne donne accès à aucune route, seulement à la vérification ou à l'enrôlement du second facteur.
 *
 * <p><b>Rotation de la clé</b> : {@code app.jwt.previous-secret} (JWT_PREVIOUS_SECRET) reste accepté en lecture le
 * temps de la transition ; l'identifiant de clé ({@code kid}, empreinte du secret) choisit la bonne clé.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USER_TYPE = "uty";
    public static final String CLAIM_TOKEN_TYPE = "tty";
    public static final String CLAIM_SESSION = "sid";
    public static final String CLAIM_SECURITY_VERSION = "sv";
    public static final String CLAIM_MFA = "mfa";
    public static final String CLAIM_PURPOSE = "pur";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_MFA = "mfa";

    public static final String USER_TYPE_ADMIN = "ADMIN";
    public static final String USER_TYPE_LEARNER = "LEARNER";

    /** Ce que permet un jeton de défi MFA. */
    public static final String PURPOSE_VERIFY = "verify";
    public static final String PURPOSE_ENROLL = "enroll";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.previous-secret:}")
    private String previousSecret;

    @Value("${app.jwt.expiration:900000}")
    private long jwtExpiration;

    @Value("${app.auth.mfa.challenge-minutes:5}")
    private int challengeMinutes;

    private SecretKey currentKey;
    private String currentKid;
    private SecretKey previousKey;
    private String previousKid;

    /** Contenu lu d'un jeton valide (signature et expiration vérifiées). */
    public record TokenClaims(String email, String tokenType, int securityVersion, UUID sessionId,
                              boolean mfaVerified, String purpose, List<String> roles, String userType) {}

    @PostConstruct
    void init() {
        int bytes = jwtSecret == null ? 0 : jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 64) {
            // Pas d'arret du demarrage (le site public doit rester servi) : mais aucune connexion ne fonctionnera
            log.error("JWT_SECRET trop court ({} octets, 64 minimum) : toute connexion échouera. Générer un secret avec `openssl rand -base64 64`.", bytes);
            return;
        }
        currentKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        currentKid = kid(jwtSecret);
        if (previousSecret != null && previousSecret.getBytes(StandardCharsets.UTF_8).length >= 64) {
            previousKey = Keys.hmacShaKeyFor(previousSecret.getBytes(StandardCharsets.UTF_8));
            previousKid = kid(previousSecret);
            log.info("Rotation de clé JWT : l'ancien secret reste accepté en lecture.");
        }
    }

    private static String kid(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private final LocatorAdapter<Key> keyLocator = new LocatorAdapter<>() {
        @Override
        protected Key locate(JwsHeader header) {
            String requested = header.getKeyId();
            if (previousKey != null && previousKid.equals(requested)) return previousKey;
            if (currentKey == null) throw new JwtException("Clé de signature non configurée");
            return currentKey;
        }
    };

    // =========================================================================
    //  ÉMISSION
    // =========================================================================

    public String generateAccessToken(AppUserDetails user, UUID sessionId, boolean mfaVerified) {
        SecretKey key = requireCurrentKey();
        List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(currentKid).and()
                .subject(user.getUsername())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_USER_TYPE, user.userType())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_SESSION, sessionId.toString())
                .claim(CLAIM_SECURITY_VERSION, user.securityVersion())
                .claim(CLAIM_MFA, mfaVerified)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpiration))
                .signWith(key)
                .compact();
    }

    /** Défi MFA : valable quelques minutes, ne donne accès qu'à la vérification ou à l'enrôlement du second facteur. */
    public String generateMfaChallengeToken(AppUserDetails user, String purpose) {
        SecretKey key = requireCurrentKey();
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(currentKid).and()
                .subject(user.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TYPE_MFA)
                .claim(CLAIM_PURPOSE, purpose)
                .claim(CLAIM_SECURITY_VERSION, user.securityVersion())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + challengeMinutes * 60_000L))
                .signWith(key)
                .compact();
    }

    private SecretKey requireCurrentKey() {
        if (currentKey == null) throw new IllegalStateException("JWT_SECRET absent ou trop court");
        return currentKey;
    }

    // =========================================================================
    //  LECTURE
    // =========================================================================

    /** @throws JwtException jeton malformé, signature invalide ou expiré */
    public TokenClaims parse(String token) {
        var claims = Jwts.parser().keyLocator(keyLocator).build().parseSignedClaims(token).getPayload();
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        Integer sv = claims.get(CLAIM_SECURITY_VERSION, Integer.class);
        String sid = claims.get(CLAIM_SESSION, String.class);
        Boolean mfa = claims.get(CLAIM_MFA, Boolean.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return new TokenClaims(claims.getSubject(), tokenType, sv == null ? -1 : sv,
                sid == null ? null : UUID.fromString(sid), Boolean.TRUE.equals(mfa),
                claims.get(CLAIM_PURPOSE, String.class), roles == null ? List.of() : roles,
                claims.get(CLAIM_USER_TYPE, String.class));
    }

    /** Jeton valide de type voulu, sinon vide (jamais d'exception : les journaux ne reçoivent que le motif). */
    public java.util.Optional<TokenClaims> parseOfType(String token, String expectedType) {
        try {
            TokenClaims claims = parse(token);
            return expectedType.equals(claims.tokenType()) ? java.util.Optional.of(claims) : java.util.Optional.empty();
        } catch (ExpiredJwtException ex) {
            log.debug("Token JWT expiré");
        } catch (MalformedJwtException | UnsupportedJwtException ex) {
            log.debug("Token JWT malformé ou non supporté");
        } catch (IllegalArgumentException ex) {
            log.debug("Token JWT vide");
        } catch (JwtException ex) {
            log.debug("Token JWT invalide : {}", ex.getMessage());
        }
        return java.util.Optional.empty();
    }

    /** E-mail d'un jeton (signature vérifiée), utilisé à la déconnexion. */
    public String getEmailFromToken(String token) {
        return parse(token).email();
    }

    public long getExpiration() {
        return jwtExpiration;
    }
}
