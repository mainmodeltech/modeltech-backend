package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.modeltech.datamasteryhub.modules.course.enums.ProposalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Propositions de modification du programme (formateur → pédagogie). Mêmes noms de champs que les types du front
 * (contrat partagé) ; réponses en JSON brut, comme le programme lui-même.
 */
public final class ProposalPayloads {

    private ProposalPayloads() {}

    /** Questions proposées pour une leçon QUIZ. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuizBankDraft {
        /** {@code lesson.id} si la leçon existe en ligne, sinon {@code lesson.clientKey}. */
        private String lessonRef;
        @Builder.Default
        private List<EvaluationPayloads.QuizQuestionEdit> questions = new ArrayList<>();
    }

    /** Ce que le formateur enregistre (et ce qui est gardé en base). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private CourseContentPayload content;
        @Builder.Default
        private List<QuizBankDraft> quizBanks = new ArrayList<>();
    }

    /** Programme et banques en ligne (écran de différences). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Live {
        private CourseContentPayload content;
        @Builder.Default
        private List<QuizBankDraft> quizBanks = new ArrayList<>();
    }

    /**
     * Une proposition. {@code id} nul : pas encore enregistrée (copie du contenu en ligne). {@code live} n'est rempli que
     * pour la relecture (ProposalDetail) et absent sinon.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Proposal {
        private String id;
        private String formationId;
        private String formationTitle;
        private ProposalStatus status;
        private String authorName;
        private String reviewerName;
        private String reviewComment;
        private LocalDateTime baseContentUpdatedAt;
        private LocalDateTime liveContentUpdatedAt;
        /** Le programme en ligne a changé depuis le début de la proposition. */
        private boolean stale;
        /** La proposition appartient au compte qui appelle. */
        private boolean own;
        /** Statut en ligne de chaque leçon existante (id → statut). */
        @Builder.Default
        private java.util.Map<String, com.modeltech.datamasteryhub.modules.course.enums.LessonStatus> liveLessonStatuses = new java.util.LinkedHashMap<>();
        private LocalDateTime submittedAt;
        private LocalDateTime reviewedAt;
        private LocalDateTime updatedAt;
        private CourseContentPayload content;
        @Builder.Default
        private List<QuizBankDraft> quizBanks = new ArrayList<>();
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Live live;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProposableFormation {
        private String formationId;
        private String formationTitle;
        /** La proposition en cours, sinon la dernière ; nulle si aucune. */
        private ProposalRef proposal;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProposalRef {
        private String id;
        private ProposalStatus status;
        private LocalDateTime updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Summary {
        private String id;
        private String formationId;
        private String formationTitle;
        private String authorName;
        private ProposalStatus status;
        private LocalDateTime submittedAt;
        private LocalDateTime updatedAt;
        private boolean stale;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Decision {
        /** Validation : publier directement les nouvelles leçons (sinon elles arrivent en brouillon). */
        private Boolean publishNewLessons;
        /** Facultatif pour une validation, obligatoire pour une demande de modifications ou un refus. */
        private String comment;
    }

    public record Count(long count) {}
}
