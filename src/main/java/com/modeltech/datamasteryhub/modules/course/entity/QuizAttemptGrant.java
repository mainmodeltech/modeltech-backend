package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Tentatives supplémentaires accordées par l'équipe à un apprenant sur un quiz (avec motif). */
@Entity
@Table(name = "quiz_attempt_grants")
@Getter @Setter @NoArgsConstructor
public class QuizAttemptGrant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, updatable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private CourseLesson lesson;

    @Column(name = "extra_attempts", nullable = false, updatable = false)
    private int extraAttempts;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "granted_by", nullable = false, updatable = false)
    private String grantedBy;
}
