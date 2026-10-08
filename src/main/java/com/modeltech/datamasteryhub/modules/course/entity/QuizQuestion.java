package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Question de la banque d'un quiz (leçon de type QUIZ). */
@Entity
@Table(name = "quiz_questions")
@Getter @Setter @NoArgsConstructor
public class QuizQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private CourseLesson lesson;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** Affichée dans la correction. */
    @Column(columnDefinition = "TEXT")
    private String explanation;
}
