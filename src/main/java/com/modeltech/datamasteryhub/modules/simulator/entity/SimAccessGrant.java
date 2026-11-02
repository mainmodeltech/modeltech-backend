package com.modeltech.datamasteryhub.modules.simulator.entity;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Droit d'usage nominatif du simulateur : forfait, période et quota d'examens chronométrés. */
@Entity
@Table(name = "sim_access_grant")
@Getter @Setter @NoArgsConstructor
public class SimAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certification_id", nullable = false)
    private SimCertification certification;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 20)
    private PlanCode planCode;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "exam_attempts_total", nullable = false)
    private int examAttemptsTotal;

    @Column(name = "exam_attempts_used", nullable = false)
    private int examAttemptsUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GrantStatus status = GrantStatus.ACTIVE;

    @Column(name = "granted_by", nullable = false)
    private String grantedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_payment_id")
    private UUID sourcePaymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    private String revokeReason;

    public boolean isUsable(LocalDateTime now) {
        return status == GrantStatus.ACTIVE && !startsAt.isAfter(now) && expiresAt.isAfter(now);
    }

    public int examAttemptsRemaining() {
        return Math.max(0, examAttemptsTotal - examAttemptsUsed);
    }
}
