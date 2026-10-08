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

/** Messages aux apprenants et questions posées depuis les leçons. */
public final class MessagingPayloads {

    private MessagingPayloads() {}

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Écrire aux apprenants d'une session (e-mail)")
    public static class SessionMessageRequest {
        @NotBlank(message = "L'objet est obligatoire")
        @Size(max = 150, message = "150 caractères maximum")
        private String subject;

        @NotBlank(message = "Le message est obligatoire")
        @Size(max = 5000, message = "5 000 caractères maximum")
        private String body;

        @Schema(description = "Apprenants destinataires ; absent : tous les apprenants de la session")
        private List<UUID> learnerIds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SessionMessageResponse {
        private UUID id;
        private String subject;
        private String body;
        private String sentBy;
        private int recipientCount;
        /** Renseigné quand l'envoi en tâche de fond est terminé. */
        private Integer deliveredCount;
        private LocalDateTime sentAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AskRequest {
        @NotBlank(message = "La question est obligatoire")
        @Size(min = 5, max = 2000, message = "Entre 5 et 2 000 caractères")
        private String question;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnswerRequest {
        @NotBlank(message = "La réponse est obligatoire")
        @Size(max = 5000, message = "5 000 caractères maximum")
        private String answer;
    }

    /** Vue apprenant d'une de ses questions. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LearnerQuestion {
        private UUID id;
        private UUID lessonId;
        private String question;
        private LocalDateTime askedAt;
        private String answer;
        private String answeredBy;
        private LocalDateTime answeredAt;
    }

    /** Vue équipe pédagogique : qui demande, à propos de quelle leçon. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SessionQuestion {
        private UUID id;
        private UUID learnerId;
        private String learnerName;
        private UUID lessonId;
        private String lessonTitle;
        private String question;
        private LocalDateTime askedAt;
        private String answer;
        private String answeredBy;
        private LocalDateTime answeredAt;
    }
}
