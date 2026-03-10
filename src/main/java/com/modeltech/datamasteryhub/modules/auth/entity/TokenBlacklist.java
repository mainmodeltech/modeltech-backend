package com.modeltech.datamasteryhub.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entrée dans la blacklist des tokens révoqués.
 *
 * On stocke le hash SHA-256 du token (pas le token brut) pour éviter
 * toute réutilisation en cas de fuite de la base.
 *
 * Un job @Scheduled nettoie les entrées expirées chaque heure.
 */
@Entity
@Table(name = "token_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash; // SHA-256 hex du token JWT

    @Column(nullable = false)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "blacklisted_at", nullable = false)
    @Builder.Default
    private LocalDateTime blacklistedAt = LocalDateTime.now();
}