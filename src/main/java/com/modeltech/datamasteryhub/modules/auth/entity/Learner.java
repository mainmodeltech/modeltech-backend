package com.modeltech.datamasteryhub.modules.auth.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Compte apprenant, distinct de {@link AdminUser}. Créé à la confirmation du paiement
 * d'une inscription ; {@code passwordHash} reste nul tant que l'apprenant n'a pas défini
 * son mot de passe via le lien reçu par e-mail.
 */
@Entity
@Table(name = "learners")
@Getter @Setter @NoArgsConstructor
public class Learner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Adresse normalisée en minuscules. */
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName = "";

    @Column(length = 50)
    private String phone;

    @Column(length = 100)
    private String country;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Echecs de mot de passe consecutifs ; remis a zero a la connexion reussie. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Connexion par mot de passe refusee jusqu a cette heure (verrouillage temporaire). */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "learner_roles",
            joinColumns = @JoinColumn(name = "learner_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public String getFullName() {
        return (firstName + " " + (lastName == null ? "" : lastName)).trim();
    }
}
