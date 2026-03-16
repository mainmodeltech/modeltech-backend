package com.modeltech.datamasteryhub.modules.networking.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Projet réalisé par un ou plusieurs alumni pendant ou après un bootcamp.
 * Contrainte métier : au moins un ProjectMember obligatoire (enforced au niveau service).
 */
@Entity
@Table(name = "projects")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Contenu ───────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tools_technologies", columnDefinition = "text[]")
    @Builder.Default
    private List<String> toolsTechnologies = new ArrayList<>();

    @Column(name = "access_link")
    private String accessLink;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    // ── Classification ────────────────────────────────────────────────────
    private String cohort;
    private Integer year;

    // ── Visibilité ────────────────────────────────────────────────────────
    @Builder.Default
    private boolean published = true;

    @Builder.Default
    @Column(name = "display_order")
    private int displayOrder = 0;

    // ── Relations ─────────────────────────────────────────────────────────
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProjectMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProjectScreenshot> screenshots = new ArrayList<>();
}
