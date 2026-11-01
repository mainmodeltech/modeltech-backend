package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Une version d'une question. {@code payload} contient les choix et le corrigé : il n'est jamais renvoyé à un apprenant
 * (l'API d'administration est la seule à le lire). Figée dès la publication.
 */
@Entity
@Table(name = "sim_item_version")
@Getter @Setter @NoArgsConstructor
public class SimItemVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private SimItem item;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType type;

    @Column(nullable = false)
    private short difficulty;

    @Column(name = "stem_en", nullable = false, columnDefinition = "TEXT")
    private String stemEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "explanation_fr", columnDefinition = "TEXT")
    private String explanationFr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ItemStatus status = ItemStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id", nullable = false)
    private SimBlueprint blueprint;

    @Column(nullable = false)
    private String author;

    private String reviewer;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    private String publisher;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Column(name = "retire_reason", columnDefinition = "TEXT")
    private String retireReason;
}
