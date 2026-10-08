package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Évaluations — mêmes noms de champs que {@code evaluation.type.ts} côté front, plus les écrans d'édition admin. */
public final class EvaluationPayloads {

    private EvaluationPayloads() {}

    // ── Apprenant : vue d'ensemble ───────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizSummary {
        private String id;
        private String lessonId;
        private String title;
        private int questionCount;
        private Integer timeLimitMinutes;
        private int passThreshold;
        private Integer maxAttempts;
        private int attemptsUsed;
        private Integer bestScore;
        /** PASSED, FAILED (tentatives épuisées), LOCKED (module pas encore ouvert), TO_DO (jamais tenté), AVAILABLE (à retenter). */
        private String status;
        private String dueLabel;
        private boolean optional;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProjectFileItem {
        private String id;
        private String name;
        private String sizeLabel;
        private LocalDateTime uploadedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProjectFeedbackItem {
        private String id;
        private String authorName;
        private String authorRole;
        private String message;
        private LocalDateTime createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProjectOverview {
        private ProjectStatus status;
        private String brief;
        private String deadlineLabel;
        private List<String> acceptedExtensions;
        private int maxSizeMb;
        private List<ProjectFileItem> files;
        private List<ProjectFeedbackItem> feedback;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CertificateCondition {
        /** LESSONS, QUIZZES, LIVES ou PROJECT. */
        private String key;
        private String label;
        private String valueLabel;
        private int percent;
        private boolean met;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EvaluationsOverview {
        private String formationId;
        private String formationTitle;
        private String cohortLabel;
        private int passThreshold;
        private List<QuizSummary> quizzes;
        private ProjectOverview project;
        private List<CertificateCondition> conditions;
    }

    // ── Apprenant : tentative de quiz ────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizChoiceView {
        private String id;
        private String label;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizQuestionView {
        private String id;
        private String text;
        private List<QuizChoiceView> choices;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizAttemptStart {
        private String attemptId;
        private String quizId;
        private String title;
        private int attemptNumber;
        private Integer maxAttempts;
        private int passThreshold;
        private Integer timeLimitMinutes;
        private LocalDateTime startedAt;
        /** Sans les bonnes réponses. */
        private List<QuizQuestionView> questions;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuizSubmission {
        /** questionId → choiceId (absent = sans réponse). */
        private Map<String, String> answers;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizReviewItem {
        private String questionId;
        private String chosenChoiceId;
        private String correctChoiceId;
        private String explanation;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizAttemptResult {
        private String attemptId;
        private String quizId;
        private int score;
        private boolean passed;
        private int correctCount;
        private int questionCount;
        private int attemptNumber;
        private Integer attemptsRemaining;
        /** Correction détaillée : fournie après une réussite ou quand il ne reste plus de tentative, sinon null. */
        private List<QuizReviewItem> review;
    }

    // ── Admin : suivi de session ─────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrackedLearner {
        private String learnerId;
        private String name;
        private String subtitle;
        private int progressPercent;
        private int quizPassed;
        private int quizTotal;
        private ProjectStatus project;
        /** READY, PENDING ou AT_RISK. */
        private String certificate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SessionLive {
        private String id;
        private String title;
        private LocalDateTime startsAt;
        /** DONE ou UPCOMING. */
        private String status;
        /** Null tant que l'appel n'a pas été fait. */
        private List<String> presentLearnerIds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SessionTracking {
        private String sessionId;
        private String formationId;
        private String formationTitle;
        private String sessionName;
        private String deliveredBy;
        private String trainerName;
        private LocalDate startDate;
        private LocalDate endDate;
        private String formatLabel;
        private String statusLabel;
        private int capacity;
        private List<TrackedLearner> learners;
        private List<SessionLive> lives;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttendanceUpdate {
        private List<String> presentLearnerIds;
    }

    // ── Admin : édition de la banque de questions ────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuizChoiceEdit {
        private String id;
        private String label;
        private Boolean correct;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuizQuestionEdit {
        private String id;
        private String text;
        private String explanation;
        @Builder.Default
        private List<QuizChoiceEdit> choices = new ArrayList<>();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuizBank {
        private String lessonId;
        private String title;
        /** Réglages de la leçon (lecture seule ici : ils se modifient dans le programme). */
        private Integer questionCount;
        private Integer passThreshold;
        private Integer maxAttempts;
        @Builder.Default
        private List<QuizQuestionEdit> questions = new ArrayList<>();
    }

    // ── Admin : projet final ─────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectConfig {
        private String brief;
        private String deadlineLabel;
        private List<String> acceptedExtensions;
        private Integer maxSizeMb;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectReview {
        /** VALIDATED ou CHANGES_REQUESTED. */
        private ProjectStatus status;
        private String message;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProjectFileLink {
        private String id;
        private String name;
        private String sizeLabel;
        /** Lien de téléchargement temporaire (15 minutes). */
        private String downloadUrl;
        private LocalDateTime uploadedAt;
    }
}
