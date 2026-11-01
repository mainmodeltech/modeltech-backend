package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** L'identité stable d'une question (code public) ; son contenu vit dans ses versions. */
@Entity
@Table(name = "sim_item")
@Getter @Setter @NoArgsConstructor
public class SimItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certification_id", nullable = false)
    private SimCertification certification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private SimBlueprintSkill skill;

    @Column(name = "public_code", nullable = false, unique = true, length = 40)
    private String publicCode;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @OrderBy("versionNo ASC")
    private List<SimItemVersion> versions = new ArrayList<>();
}
