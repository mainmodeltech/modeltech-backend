package com.modeltech.datamasteryhub.modules.simulator.entity;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Une tentative d'un apprenant. L'échéance est stockée côté serveur ; le verrou optimiste évite les écritures croisées. */
@Entity
@Table(name = "sim_attempt")
@Getter @Setter @NoArgsConstructor
public class SimAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private SimAccessGrant grant;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false, unique = true)
    private SimExamForm form;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private AttemptMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "score_percent", precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Column(name = "scaled_score")
    private Integer scaledScore;

    private Boolean passed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Version
    private long version;
}
