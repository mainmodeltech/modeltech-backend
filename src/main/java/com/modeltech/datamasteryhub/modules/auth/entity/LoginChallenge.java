package com.modeltech.datamasteryhub.modules.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Demande de connexion sans mot de passe : un lien et un code à 6 chiffres, tous deux à usage unique.
 * Seuls les empreintes sont conservées (une fuite de la base ne donne aucune connexion).
 */
@Entity
@Table(name = "login_challenges")
@Getter @Setter @NoArgsConstructor
public class LoginChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(nullable = false)
    private boolean consumed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isUsable() {
        return !consumed && LocalDateTime.now().isBefore(expiresAt);
    }
}
