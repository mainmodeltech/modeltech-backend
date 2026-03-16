package com.modeltech.datamasteryhub.modules.networking.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Représente un apprenant ayant terminé un bootcamp.
 * Lien optionnel vers Registration (peut être créé manuellement depuis le backoffice).
 */
@Entity
@Table(name = "alumni")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Alumni extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Lien optionnel vers l'inscription d'origine ────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id")
    private Registration registration;

    // ── Identité ───────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    // ── Situation professionnelle ──────────────────────────────────────────
    @Column(name = "current_title")
    private String currentTitle;

    @Column(name = "current_position")
    private String currentPosition;

    // ── Réseaux / médias ───────────────────────────────────────────────────
    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "photo_url")
    private String photoUrl;

    // ── Promotion ─────────────────────────────────────────────────────────
    private String cohort;
    private Integer year;

    @Column(name = "bootcamp_title")
    private String bootcampTitle;

    // ── Visibilité ────────────────────────────────────────────────────────
    @Builder.Default
    private boolean published = true;

    @Builder.Default
    @Column(name = "display_order")
    private int displayOrder = 0;

    // ── Relation inverse (lectures seules) ────────────────────────────────
    @OneToMany(mappedBy = "alumni", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProjectMember> projectMemberships = new ArrayList<>();
}
