package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Événements horodatés d'une tentative (démarrage, remise, expiration) : preuve en cas de litige. */
@Entity
@Table(name = "sim_attempt_event")
@Getter @Setter @NoArgsConstructor
public class SimAttemptEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private SimAttempt attempt;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> meta;
}
