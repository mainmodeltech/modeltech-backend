package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Une sous-compétence mesurée (ex. « Create model calculations by using DAX ») et le nombre de questions visé. */
@Entity
@Table(name = "sim_blueprint_skill")
@Getter @Setter @NoArgsConstructor
public class SimBlueprintSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domain_id", nullable = false)
    private SimBlueprintDomain domain;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(name = "target_questions", nullable = false)
    private int targetQuestions;
}
