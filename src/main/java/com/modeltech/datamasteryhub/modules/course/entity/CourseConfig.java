package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Réglages du cours et règles du certificat d'une formation. */
@Entity
@Table(name = "course_configs")
@Getter @Setter @NoArgsConstructor
public class CourseConfig extends BaseEntity {

    public static final String ACCESS_12_MONTHS = "12_MONTHS";
    public static final String ACCESS_LIFETIME = "LIFETIME";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bootcamp_id", nullable = false, updatable = false)
    private Bootcamp bootcamp;

    @Column(name = "sequential_unlock", nullable = false)
    private boolean sequentialUnlock;

    /** {@link #ACCESS_12_MONTHS} ou {@link #ACCESS_LIFETIME}. */
    @Column(name = "access_duration", nullable = false, length = 20)
    private String accessDuration;

    @Column(name = "lessons_completed_percent", nullable = false)
    private int lessonsCompletedPercent;

    @Column(name = "quiz_pass_percent", nullable = false)
    private int quizPassPercent;

    @Column(name = "live_presence_percent", nullable = false)
    private int livePresencePercent;

    @Column(name = "final_project_validated", nullable = false)
    private boolean finalProjectValidated;

    @Column(name = "certificate_template", nullable = false)
    private String certificateTemplate;

    /** Dernière sauvegarde du programme (affichée dans l'éditeur). */
    @Column(name = "content_updated_at")
    private LocalDateTime contentUpdatedAt;
}
