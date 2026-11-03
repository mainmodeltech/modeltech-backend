package com.modeltech.datamasteryhub.modules.legal.entity;

import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.LegalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une version d'un texte légal. Brouillon modifiable ; une fois publiée elle est figée (le service n'écrit plus jamais dessus) et remplacée par
 * une nouvelle version quand le texte change. Les acceptations renvoient à cette version et à l'empreinte de son contenu.
 */
@Entity
@Table(name = "legal_document")
@Getter @Setter @NoArgsConstructor
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LegalCode code;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LegalStatus status = LegalStatus.DRAFT;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "counsel_validated_by")
    private String counselValidatedBy;

    @Column(name = "counsel_validated_on")
    private LocalDate counselValidatedOn;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
