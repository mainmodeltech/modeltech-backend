package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Certificat de réussite. Les informations imprimées sont figées à la délivrance : le PDF est
 * regénéré à la demande à partir de cette photographie, il ne change donc pas si la formation est renommée.
 */
@Entity
@Table(name = "certificates")
@Getter @Setter @NoArgsConstructor
public class Certificate extends BaseEntity {

    public static final String VALID = "VALID";
    public static final String REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Numéro public, ex. {@code MT-2026-VBA-00042-K7QX} : seul identifiant exposé (page de vérification, QR code). */
    @Column(name = "public_id", nullable = false, updatable = false, length = 40)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, updatable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bootcamp_id", nullable = false, updatable = false)
    private Bootcamp bootcamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", updatable = false)
    private BootcampSession session;

    @Column(name = "recipient_name", nullable = false, updatable = false)
    private String recipientName;

    @Column(name = "formation_title", nullable = false, updatable = false)
    private String formationTitle;

    @Column(name = "duration_label", length = 100, updatable = false)
    private String durationLabel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", updatable = false)
    private List<String> skills;

    @Column(name = "includes_project", nullable = false, updatable = false)
    private boolean includesProject;

    @Column(name = "signatory_name", nullable = false, updatable = false)
    private String signatoryName;

    @Column(name = "signatory_title", nullable = false, updatable = false)
    private String signatoryTitle;

    @Column(name = "trainer_name", updatable = false)
    private String trainerName;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    /** « système » pour une délivrance automatique, sinon l'e-mail de la personne qui a délivré. */
    @Column(name = "issued_by", nullable = false, updatable = false)
    private String issuedBy;

    /** Délivré malgré des conditions non remplies (dérogation motivée). */
    @Column(nullable = false, updatable = false)
    private boolean forced;

    @Column(name = "force_reason", columnDefinition = "TEXT", updatable = false)
    private String forceReason;

    @Column(nullable = false, length = 10)
    private String status = VALID;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", columnDefinition = "TEXT")
    private String revokedReason;

    public boolean isValid() {
        return VALID.equals(status);
    }
}
