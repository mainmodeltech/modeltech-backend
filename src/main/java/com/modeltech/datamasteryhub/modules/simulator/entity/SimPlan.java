package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un forfait en vente : prix, durée d'accès et nombre d'examens blancs. Le prix facturé vient toujours d'ici, jamais du navigateur. */
@Entity
@Table(name = "sim_plan")
@Getter @Setter @NoArgsConstructor
public class SimPlan {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlanCode code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "price_xof", nullable = false)
    private int priceXof;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "exam_attempts", nullable = false)
    private int examAttempts;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
