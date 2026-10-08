package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Une tentative de quiz : questions tirées et réponses figées, correction faite par le serveur. */
@Entity
@Table(name = "quiz_attempts")
@Getter @Setter @NoArgsConstructor
public class QuizAttempt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, updatable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private CourseLesson lesson;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private Integer attemptNumber;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    /** Nulle tant que la tentative n'est pas rendue. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Identifiants des questions tirées, dans l'ordre présenté. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> questionIds;

    /** questionId → choiceId choisi. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> answers;

    @Column(name = "correct_count")
    private Integer correctCount;

    private Integer score;

    private Boolean passed;

    public boolean isSubmitted() {
        return submittedAt != null;
    }
}
