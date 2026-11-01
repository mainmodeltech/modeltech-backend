package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un domaine de compétences (ex. « Model the data », 25–30 % de l'examen). */
@Entity
@Table(name = "sim_blueprint_domain")
@Getter @Setter @NoArgsConstructor
public class SimBlueprintDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id", nullable = false)
    private SimBlueprint blueprint;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "weight_min", nullable = false)
    private int weightMin;

    @Column(name = "weight_max", nullable = false)
    private int weightMax;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "domain", fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<SimBlueprintSkill> skills = new ArrayList<>();
}
