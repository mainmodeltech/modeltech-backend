package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Message envoyé par l'équipe aux apprenants d'une session (historique). */
@Entity
@Table(name = "session_messages")
@Getter @Setter @NoArgsConstructor
public class SessionMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private BootcampSession session;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "sent_by", nullable = false, updatable = false)
    private String sentBy;

    @Column(name = "recipient_count", nullable = false, updatable = false)
    private Integer recipientCount;

    /** Renseigné quand l'envoi en tâche de fond est terminé. */
    @Column(name = "delivered_count")
    private Integer deliveredCount;
}
