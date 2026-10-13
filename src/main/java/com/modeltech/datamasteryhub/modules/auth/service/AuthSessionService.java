package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.common.util.TokenUtils;
import com.modeltech.datamasteryhub.modules.auth.entity.AuthSession;
import com.modeltech.datamasteryhub.modules.auth.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessions de connexion et jetons de rafraîchissement : une session par appareil, jeton à usage unique qui change
 * à chaque rafraîchissement (rotation), détection du rejeu, révocation individuelle ou totale.
 *
 * <p>Durées : personnel — inactivité 8 h, session 7 jours au plus ; apprenants — 14 jours d'inactivité, 60 jours au plus
 * ({@code app.auth.refresh.*}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionService {

    private final AuthSessionRepository repository;

    @Value("${app.auth.refresh.staff-idle-hours:8}")
    private int staffIdleHours;

    @Value("${app.auth.refresh.staff-max-days:7}")
    private int staffMaxDays;

    @Value("${app.auth.refresh.learner-idle-days:14}")
    private int learnerIdleDays;

    @Value("${app.auth.refresh.learner-max-days:60}")
    private int learnerMaxDays;

    /** Deux onglets qui rafraîchissent presque en même temps présentent le même jeton : toléré quelques secondes. */
    @Value("${app.auth.refresh.grace-seconds:10}")
    private int graceSeconds;

    /** Session ouverte : le jeton de rafraîchissement n'est donné qu'ici (non récupérable ensuite). */
    public record Issued(UUID sessionId, String refreshToken, LocalDateTime expiresAt) {}

    /** Résultat d'un rafraîchissement ; {@code newRefreshToken} est nul quand le cookie existant reste valable (cas des onglets simultanés). */
    public record Refreshed(AuthSession session, String newRefreshToken) {}

    @Transactional
    public Issued start(String subjectType, UUID subjectId, boolean mfaVerified, ClientInfo client) {
        LocalDateTime now = LocalDateTime.now();
        boolean staff = AuthSession.ADMIN.equals(subjectType);
        String raw = TokenUtils.randomToken();
        AuthSession session = new AuthSession();
        session.setSubjectType(subjectType);
        session.setSubjectId(subjectId);
        session.setTokenHash(sha256(raw));
        session.setExpiresAt(now.plusHours(staff ? staffIdleHours : learnerIdleDays * 24L));
        session.setMaxExpiresAt(now.plusDays(staff ? staffMaxDays : learnerMaxDays));
        session.setMfaVerified(mfaVerified);
        session.setIp(client.ip());
        session.setUserAgent(client.userAgent());
        repository.save(session);
        return new Issued(session.getId(), raw, session.getExpiresAt());
    }

    /** Échange un jeton de rafraîchissement contre une session prolongée, ou vide s'il est inconnu, expiré, révoqué ou rejoué. */
    @Transactional
    public Optional<Refreshed> refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        String hash = sha256(rawToken);

        Optional<AuthSession> current = repository.findByTokenHash(hash);
        if (current.isPresent()) {
            AuthSession session = current.get();
            if (!session.isActive(now)) return Optional.empty();
            boolean staff = AuthSession.ADMIN.equals(session.getSubjectType());
            LocalDateTime idle = now.plusHours(staff ? staffIdleHours : learnerIdleDays * 24L);
            String next = TokenUtils.randomToken();
            session.setPreviousTokenHash(hash);
            session.setTokenHash(sha256(next));
            session.setRotatedAt(now);
            session.setLastUsedAt(now);
            session.setExpiresAt(idle.isBefore(session.getMaxExpiresAt()) ? idle : session.getMaxExpiresAt());
            repository.save(session);
            return Optional.of(new Refreshed(session, next));
        }

        Optional<AuthSession> replayed = repository.findByPreviousTokenHash(hash);
        if (replayed.isPresent()) {
            AuthSession session = replayed.get();
            boolean concurrent = session.getRotatedAt() != null && session.getRotatedAt().plusSeconds(graceSeconds).isAfter(now);
            if (concurrent && session.isActive(now)) {
                return Optional.of(new Refreshed(session, null));
            }
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                repository.save(session);
                log.warn("Jeton de rafraîchissement rejoué : session {} révoquée", session.getId());
            }
        }
        return Optional.empty();
    }

    /** Termine la session portée par ce jeton (déconnexion). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String hash = sha256(rawToken);
        repository.findByTokenHash(hash).or(() -> repository.findByPreviousTokenHash(hash)).ifPresent(s -> {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(LocalDateTime.now());
                repository.save(s);
            }
        });
    }

    /** Termine toutes les sessions d'un compte (changement de mot de passe, désactivation, réinitialisation du MFA). */
    @Transactional
    public void revokeAllFor(String subjectType, UUID subjectId) {
        // Entités chargées puis modifiées (et non une mise à jour en masse) : les lectures suivantes de la même transaction voient la révocation
        LocalDateTime now = LocalDateTime.now();
        List<AuthSession> active = repository.findAllBySubjectTypeAndSubjectIdAndRevokedAtIsNull(subjectType, subjectId);
        active.forEach(session -> session.setRevokedAt(now));
        repository.saveAll(active);
        if (!active.isEmpty()) log.info("{} session(s) terminée(s) pour le compte {}", active.size(), subjectId);
    }

    @Transactional
    public int cleanup() {
        return repository.deleteEndedBefore(LocalDateTime.now().minusDays(30));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
