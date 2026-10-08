package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_lessons")
@Getter @Setter @NoArgsConstructor
public class CourseLesson extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonStatus status;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Identifiant chez l'hébergeur vidéo (lecture HLS / URL signée) — jamais un objet MinIO brut. */
    @Column(name = "video_provider_id")
    private String videoProviderId;

    @Column(name = "video_url", length = 2048)
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Heure locale du site (pas de fuseau). */
    @Column(name = "live_at")
    private LocalDateTime liveAt;

    @Column(name = "live_url", length = 2048)
    private String liveUrl;

    @Column(name = "quiz_question_count")
    private Integer quizQuestionCount;

    @Column(name = "quiz_pass_threshold")
    private Integer quizPassThreshold;

    /** Nulle = tentatives illimitées. */
    @Column(name = "quiz_max_attempts")
    private Integer quizMaxAttempts;
}
