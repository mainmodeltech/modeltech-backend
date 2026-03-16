package com.modeltech.datamasteryhub.modules.networking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table de jointure entre Project et Alumni.
 * Pas de soft delete — suppression directe (CASCADE depuis Project).
 * Contrainte UNIQUE (project_id, alumni_id) en BDD.
 */
@Entity
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_alumni",
                columnNames = {"project_id", "alumni_id"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alumni_id", nullable = false)
    private Alumni alumni;

    /** Rôle de l'alumni dans ce projet (ex: "Data Analyst", "Lead BI") */
    private String role;

    @Builder.Default
    @Column(name = "display_order")
    private int displayOrder = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
