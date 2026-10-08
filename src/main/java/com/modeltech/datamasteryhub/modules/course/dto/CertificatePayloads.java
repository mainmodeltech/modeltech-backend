package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Certificats : page de vérification publique, espace apprenant, back-office. */
public final class CertificatePayloads {

    private CertificatePayloads() {}

    /** Page publique de vérification : le strict nécessaire pour authentifier le certificat (pas d'e-mail ni de contact). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PublicCertificate {
        private String publicId;
        /** VALID ou REVOKED. */
        private String status;
        private String recipientName;
        private String formationTitle;
        private String durationLabel;
        private List<String> skills;
        private boolean includesProject;
        private LocalDateTime issuedAt;
        private String issuer;
        private String signatoryName;
        private String signatoryTitle;
        private LocalDateTime revokedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LearnerCertificate {
        private String publicId;
        private String formationId;
        private String formationTitle;
        private LocalDateTime issuedAt;
        private String status;
        /** Page de vérification (à partager). */
        private String verifyUrl;
        /** Chemin du PDF sur l'API (à préfixer par l'adresse de l'API). */
        private String pdfPath;
        /** Lien « Ajouter à mon profil LinkedIn ». */
        private String linkedInUrl;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdminCertificate {
        private UUID id;
        private String publicId;
        private UUID learnerId;
        private String learnerName;
        private String learnerEmail;
        private UUID formationId;
        private String formationTitle;
        private UUID sessionId;
        private String sessionName;
        private LocalDateTime issuedAt;
        private String issuedBy;
        private boolean forced;
        private String forceReason;
        private String status;
        private LocalDateTime revokedAt;
        private String revokedReason;
        private String verifyUrl;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Délivrer le certificat d'un apprenant")
    public static class IssueRequest {
        @Schema(description = "Délivrer même si toutes les conditions ne sont pas remplies (ADMIN et SUPER_ADMIN, motif obligatoire)")
        private Boolean force;

        @Size(max = 1000)
        private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Révoquer un certificat")
    public static class RevokeRequest {
        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 1000)
        private String reason;
    }
}
