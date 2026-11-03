package com.modeltech.datamasteryhub.modules.legal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Preuve d'acceptation : qui, quelle version exacte de quel texte, quand, pour quel acte (commande, candidature, demande…). Jamais modifiée. */
@Entity
@Table(name = "legal_acceptance")
@Getter @Setter @NoArgsConstructor
public class LegalAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private LegalDocument document;

    @Column(name = "document_code", nullable = false, length = 12, updatable = false)
    private String documentCode;

    @Column(name = "document_version", nullable = false, updatable = false)
    private int documentVersion;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Column(nullable = false, length = 30, updatable = false)
    private String context;

    @Column(name = "subject_type", nullable = false, length = 30, updatable = false)
    private String subjectType;

    @Column(name = "subject_ref", nullable = false, length = 64, updatable = false)
    private String subjectRef;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(name = "full_name", updatable = false)
    private String fullName;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private LocalDateTime acceptedAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;
}
