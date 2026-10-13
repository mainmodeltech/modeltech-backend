package com.modeltech.datamasteryhub.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Jetons d'accès (15 min) et défis MFA : contenu, types, rotation de clé, falsification. */
class JwtTokenProviderTest {

    private static final String SECRET = "A".repeat(64);
    private static final String OLD_SECRET = "B".repeat(64);

    private static JwtTokenProvider provider(String secret, String previous, long expirationMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "previousSecret", previous);
        ReflectionTestUtils.setField(p, "jwtExpiration", expirationMs);
        ReflectionTestUtils.setField(p, "challengeMinutes", 5);
        p.init();
        return p;
    }

    private static AppUserDetails staff(int version) {
        return new AppUserDetails("staff@example.com", "x", true, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                UUID.randomUUID(), "Équipe", JwtTokenProvider.USER_TYPE_ADMIN, version, true);
    }

    @Test
    void accessTokenCarriesTheSessionTheSecurityVersionAndTheSecondFactorState() {
        JwtTokenProvider jwt = provider(SECRET, "", 900_000);
        UUID sid = UUID.randomUUID();

        String token = jwt.generateAccessToken(staff(7), sid, true);
        var claims = jwt.parse(token);

        assertThat(claims.email()).isEqualTo("staff@example.com");
        assertThat(claims.tokenType()).isEqualTo("access");
        assertThat(claims.sessionId()).isEqualTo(sid);
        assertThat(claims.securityVersion()).isEqualTo(7);
        assertThat(claims.mfaVerified()).isTrue();
        assertThat(claims.roles()).containsExactly("ROLE_ADMIN");
        assertThat(claims.userType()).isEqualTo("ADMIN");
        assertThat(jwt.getExpiration()).isEqualTo(900_000);
    }

    @Test
    void anMfaChallengeIsNotAnAccessToken() {
        JwtTokenProvider jwt = provider(SECRET, "", 900_000);
        String challenge = jwt.generateMfaChallengeToken(staff(3), JwtTokenProvider.PURPOSE_ENROLL);

        assertThat(jwt.parseOfType(challenge, JwtTokenProvider.TYPE_ACCESS)).isEmpty();
        var claims = jwt.parseOfType(challenge, JwtTokenProvider.TYPE_MFA).orElseThrow();
        assertThat(claims.purpose()).isEqualTo("enroll");
        assertThat(claims.securityVersion()).isEqualTo(3);
        assertThat(claims.roles()).isEmpty();   // aucun droit porté par un défi

        String access = jwt.generateAccessToken(staff(3), UUID.randomUUID(), true);
        assertThat(jwt.parseOfType(access, JwtTokenProvider.TYPE_MFA)).isEmpty();
    }

    @Test
    void expiredMalformedAndForgedTokensAreRefused() {
        JwtTokenProvider jwt = provider(SECRET, "", 900_000);
        String expired = Jwts.builder().subject("staff@example.com").claim("tty", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000)).expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(jwt.parseOfType(expired, "access")).isEmpty();
        assertThat(jwt.parseOfType("n-importe-quoi", "access")).isEmpty();
        assertThat(jwt.parseOfType("", "access")).isEmpty();

        String forged = provider("C".repeat(64), "", 900_000).generateAccessToken(staff(0), UUID.randomUUID(), true);
        assertThat(jwt.parseOfType(forged, "access")).isEmpty();

        // algorithme « none » : refusé
        String unsigned = Jwts.builder().subject("staff@example.com").claim("tty", "access").compact();
        assertThat(jwt.parseOfType(unsigned, "access")).isEmpty();
        assertThatThrownBy(() -> jwt.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void keyRotation_acceptsTheOldSecretForReading_untilItIsRemoved() {
        String signedWithOld = provider(OLD_SECRET, "", 900_000).generateAccessToken(staff(1), UUID.randomUUID(), true);

        assertThat(provider(SECRET, OLD_SECRET, 900_000).parseOfType(signedWithOld, "access")).isPresent();   // pendant la transition
        assertThat(provider(SECRET, "", 900_000).parseOfType(signedWithOld, "access")).isEmpty();             // transition terminée

        // les nouveaux jetons sont signés avec la nouvelle clé
        JwtTokenProvider rotating = provider(SECRET, OLD_SECRET, 900_000);
        String fresh = rotating.generateAccessToken(staff(1), UUID.randomUUID(), true);
        assertThat(provider(SECRET, "", 900_000).parseOfType(fresh, "access")).isPresent();
        assertThat(provider(OLD_SECRET, "", 900_000).parseOfType(fresh, "access")).isEmpty();
    }

    @Test
    void aTooShortSecretMeansNoTokenCanBeIssuedNorAccepted() {
        JwtTokenProvider weak = provider("court", "", 900_000);

        assertThatThrownBy(() -> weak.generateAccessToken(staff(0), UUID.randomUUID(), true)).isInstanceOf(IllegalStateException.class);
        String valid = provider(SECRET, "", 900_000).generateAccessToken(staff(0), UUID.randomUUID(), true);
        assertThat(weak.parseOfType(valid, "access")).isEmpty();
    }
}
