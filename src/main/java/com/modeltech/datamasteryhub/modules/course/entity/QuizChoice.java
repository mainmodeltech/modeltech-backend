package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "quiz_choices")
@Getter @Setter @NoArgsConstructor
public class QuizChoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 500)
    private String label;

    /** Jamais envoyée à l'apprenant avant la soumission. */
    @Column(nullable = false)
    private boolean correct;
}
