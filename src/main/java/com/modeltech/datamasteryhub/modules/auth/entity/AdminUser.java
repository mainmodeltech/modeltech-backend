package com.modeltech.datamasteryhub.modules.auth.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité AdminUser — version RBAC.
 *
 * Changements par rapport à V1 :
 *  - Le champ {@code role} (String) est remplacé par une relation @ManyToMany vers {@link Role}.
 *  - Compatibilité ascendante : la méthode {@code getPrimaryRole()} retourne le premier rôle
 *    pour les APIs qui attendent encore un String.
 */
@Entity
@Table(name = "admin_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    // ─── RBAC : remplace l'ancien champ role (String) ──────────────────────
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "admin_user_roles",
            joinColumns        = @JoinColumn(name = "admin_user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // ──────────────────────────────────────────────────────────────────────
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Echecs de mot de passe consecutifs ; remis a zero a la connexion reussie. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Connexion par mot de passe refusee jusqu a cette heure (verrouillage temporaire). */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Incrementee a chaque evenement de securite (mot de passe, role, desactivation, MFA) : invalide les jetons deja emis. */
    @Column(name = "security_version", nullable = false)
    @Builder.Default
    private int securityVersion = 0;

    /** Secret TOTP chiffre ; le MFA n'est actif que si {@link #mfaEnabledAt} est renseigne. */
    @Column(name = "mfa_secret_enc")
    private String mfaSecretEnc;

    @Column(name = "mfa_enabled_at")
    private LocalDateTime mfaEnabledAt;

    /** Dernier pas de temps TOTP accepte : un code deja utilise ne sert pas deux fois. */
    @Column(name = "mfa_last_step")
    private Long mfaLastStep;

    @Column(name = "mfa_failed_attempts", nullable = false)
    @Builder.Default
    private int mfaFailedAttempts = 0;

    @Column(name = "mfa_locked_until")
    private LocalDateTime mfaLockedUntil;

    /** Partenaire rattaché (comptes ROLE_PARTNER : limités à leurs propres formations). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    public boolean isMfaEnabled() {
        return mfaEnabledAt != null && mfaSecretEnc != null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /**
     * Retourne le nom du premier rôle (pour compatibilité avec AuthResponse).
     * Préférer {@link #getRoles()} pour une logique RBAC complète.
     */
    public String getPrimaryRole() {
        return roles.stream()
                .map(Role::getName)
                .findFirst()
                .orElse("ROLE_ADMIN");
    }

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }
}