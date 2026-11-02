package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Une version du guide de compétences d'une certification : domaines, poids, durée et score de réussite. */
@Entity
@Table(name = "sim_blueprint")
@Getter @Setter @NoArgsConstructor
public class SimBlueprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certification_id", nullable = false)
    private SimCertification certification;

    @Column(name = "version_date", nullable = false)
    private LocalDate versionDate;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "questions_min", nullable = false)
    private int questionsMin;

    @Column(name = "questions_max", nullable = false)
    private int questionsMax;

    @Column(name = "pass_score", nullable = false)
    private int passScore;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "blueprint", fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<SimBlueprintDomain> domains = new ArrayList<>();
}
