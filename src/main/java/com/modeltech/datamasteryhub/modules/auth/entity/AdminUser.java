package com.modeltech.datamasteryhub.modules.auth.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
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