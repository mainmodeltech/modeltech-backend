package com.modeltech.datamasteryhub.modules.audit.entity;

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
 * Une ligne du journal d'audit. Ajout seul : la base interdit la modification et la suppression (déclencheur V38), donc
 * l'entité n'a ni colonnes de mise à jour ni suppression logique.
 */
@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Column(name = "actor_name", updatable = false)
    private String actorName;

    @Column(name = "actor_profiles", updatable = false)
    private String actorProfiles;

    @Column(nullable = false, updatable = false, length = 60)
    private String action;

    @Column(nullable = false, updatable = false, length = 30)
    private String category;

    @Column(name = "entity_type", updatable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 64)
    private String entityId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(updatable = false, length = 30)
    private String flag;

    @Column(updatable = false, length = 64)
    private String ip;
}
