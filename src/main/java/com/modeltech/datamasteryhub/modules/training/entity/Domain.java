package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Domaine du catalogue (ex. « Data & BI », « Gestion de projet & Agile »).
 * Regroupe plusieurs formations.
 */
@Entity
@Table(name = "domains")
@Getter @Setter @NoArgsConstructor
public class Domain extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Badge court de la carte domaine, ex. « NOTRE SPÉCIALITÉ ». */
    @Column(length = 100)
    private String badge;

    /** Domaine annoncé mais sans formation publiée (carte « Prochain domaine »). */
    @Column(name = "coming_soon", nullable = false)
    private Boolean comingSoon = false;

    @Column(nullable = false)
    private Boolean visible = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
