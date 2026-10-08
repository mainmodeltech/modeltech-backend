package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Rendu du projet final d'un apprenant (un par formation). */
@Entity
@Table(name = "project_submissions")
@Getter @Setter @NoArgsConstructor
public class ProjectSubmission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, updatable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bootcamp_id", nullable = false, updatable = false)
    private Bootcamp bootcamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectStatus status = ProjectStatus.NOT_STARTED;
}
