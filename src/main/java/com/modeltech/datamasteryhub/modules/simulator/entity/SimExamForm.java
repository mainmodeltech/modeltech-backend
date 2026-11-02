package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Instantané d'un examen assemblé : les versions de questions exactes proposées à une tentative. */
@Entity
@Table(name = "sim_exam_form")
@Getter @Setter @NoArgsConstructor
public class SimExamForm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blueprint_id", nullable = false)
    private SimBlueprint blueprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private AttemptMode mode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "form", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("position ASC")
    private List<SimExamFormItem> items = new ArrayList<>();
}
