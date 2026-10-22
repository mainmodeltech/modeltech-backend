package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.course.enums.ProposalStatus;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Proposition de modification du programme d'une formation, faite par un formateur et validée par la pédagogie.
 * Le contenu proposé ({@code {content, quizBanks}}) est gardé tel quel : rien n'est écrit dans le programme avant la validation.
 */
@Entity
@Table(name = "course_content_proposals")
@Getter @Setter @NoArgsConstructor
public class CourseContentProposal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bootcamp_id", nullable = false, updatable = false)
    private Bootcamp bootcamp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private AdminUser author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    /** {@code {content: CourseContentPayload, quizBanks: QuizBankDraft[]}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** Date du programme en ligne au début de la proposition (nulle s'il n'avait jamais été enregistré). */
    @Column(name = "base_content_updated_at")
    private LocalDateTime baseContentUpdatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private AdminUser reviewer;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
