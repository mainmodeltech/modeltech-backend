package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Fichier déposé par l'apprenant. Stocké sous une clé aléatoire ; jamais d'URL publique (lien signé à la demande). */
@Entity
@Table(name = "project_files")
@Getter @Setter @NoArgsConstructor
public class ProjectFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private ProjectSubmission submission;

    @Column(nullable = false)
    private String name;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;
}
