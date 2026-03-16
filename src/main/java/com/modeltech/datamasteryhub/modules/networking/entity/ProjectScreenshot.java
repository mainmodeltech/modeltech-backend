package com.modeltech.datamasteryhub.modules.networking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Screenshot d'un projet. Image stockée dans MinIO.
 * objectKey permet la suppression propre depuis le bucket.
 */
@Entity
@Table(name = "project_screenshots")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProjectScreenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** URL publique de l'image (MinIO presigned ou publique) */
    @Column(name = "photo_url", nullable = false)
    private String photoUrl;

    /** Clé objet MinIO — nécessaire pour la suppression */
    @Column(name = "object_key", nullable = false)
    private String objectKey;

    private String caption;

    @Builder.Default
    @Column(name = "display_order")
    private int displayOrder = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
