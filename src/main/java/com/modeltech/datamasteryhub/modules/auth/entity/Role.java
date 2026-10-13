package com.modeltech.datamasteryhub.modules.auth.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name; // ex: ROLE_SUPER_ADMIN, ROLE_ADMIN, ROLE_EDITOR

    @Column(length = 255)
    private String description;

    /** Nom affiché (« Finance », « Pédagogie »…) ; à défaut le nom technique. */
    @Column(length = 100)
    private String label;

    /** Profil livré avec la plateforme : modifiable mais non supprimable. */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = true;

    /** Profil du back-office (faux : apprenant). */
    @Column(name = "is_staff", nullable = false)
    @Builder.Default
    private boolean staff = true;

    /**
     * Droits du profil (noms du catalogue {@code Permission}). Vide pour ROLE_SUPER_ADMIN : il a tous les droits, y compris
     * ceux ajoutés plus tard, sans qu'ils soient stockés.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 60)
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    public String displayName() {
        return label != null && !label.isBlank() ? label : name;
    }
}