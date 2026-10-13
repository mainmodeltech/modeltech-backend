package com.modeltech.datamasteryhub.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une session ouverte sur un appareil. Le jeton de rafraîchissement (cookie {@code HttpOnly}) n'est jamais
 * stocké en clair : seule son empreinte SHA-256 l'est. À chaque rafraîchissement le jeton change (rotation) ;
 * l'empreinte du précédent est gardée quelques secondes pour tolérer deux onglets qui rafraîchissent en
 * même temps, au-delà c'est un rejeu : la session est révoquée.
 */
@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AuthSession {

    public static final String ADMIN = "ADMIN";
    public static final String LEARNER = "LEARNER";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subject_type", nullable = false, length = 10)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt = LocalDateTime.now();

    /** Fin de validité en cas d'inactivité (glissante : repoussée à chaque rafraîchissement). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Limite absolue : au-delà, une nouvelle connexion est exigée quoi qu'il arrive. */
    @Column(name = "max_expires_at", nullable = false)
    private LocalDateTime maxExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "mfa_verified", nullable = false)
    private boolean mfaVerified;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now) && maxExpiresAt.isAfter(now);
    }
}
