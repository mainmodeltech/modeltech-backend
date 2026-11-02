package com.modeltech.datamasteryhub.modules.simulator.dto;

import com.modeltech.datamasteryhub.modules.simulator.entity.AttemptMode;
import com.modeltech.datamasteryhub.modules.simulator.entity.AttemptStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrats de l'API apprenant du simulateur. Règle de sécurité n° 1 : <b>aucun type de ce fichier ne porte de corrigé
 * pendant une tentative</b> ({@link QuestionView} ne contient que l'énoncé et les choix sans indicateur de bonne
 * réponse). Les corrigés n'apparaissent que dans {@link QuestionReport} (tentative terminée) et dans {@link Feedback}
 * (mode étude). Un test automatique vérifie cette règle.
 */
public final class LearnerSimulatorPayloads {

    private LearnerSimulatorPayloads() {}

    public record AccessView(String certification, String planCode, LocalDateTime startsAt, LocalDateTime expiresAt,
                             int examAttemptsTotal, int examAttemptsRemaining) {}

    public record DomainInfo(String code, String name, int weightMin, int weightMax, int publishedQuestions) {}

    public record CatalogView(String certification, String name, String blueprintLabel, int durationMinutes, int examQuestions,
                              int passScore, List<DomainInfo> domains, List<AccessView> access, boolean examInProgress,
                              boolean trial, int trialCorrections) {}

    public record StartRequest(@NotNull AttemptMode mode, @Size(max = 10) String domain, @Size(max = 30) String skill,
                               @Min(1) @Max(50) Integer count) {}

    /** Une question telle que la voit le candidat : jamais de corrigé. */
    public record QuestionView(UUID ref, int position, ItemType type, String stem, Map<String, Object> payload,
                               Map<String, Object> answer, boolean flagged) {}

    public record AttemptView(UUID id, AttemptMode mode, AttemptStatus status, LocalDateTime startedAt, LocalDateTime deadlineAt,
                              LocalDateTime serverNow, int totalQuestions, int answered, List<QuestionView> questions, boolean trial) {}

    public record AnswerRequest(Map<String, Object> answer, Boolean flagged, @Min(0) @Max(36000) Integer timeSpentSeconds) {}

    /** Correction immédiate : renvoyée uniquement en mode étude. */
    public record Feedback(double score, Map<String, Object> correctAnswer, String explanationFr) {}

    public record AnswerResult(boolean saved, Feedback feedback) {}

    public record AttemptSummary(UUID id, AttemptMode mode, AttemptStatus status, LocalDateTime startedAt, LocalDateTime finishedAt,
                                 int totalQuestions, Double scorePercent, Integer scaledScore, Boolean passed) {}

    public record ScoreBucket(String code, String name, int questions, double points, int percent) {}

    /** Question corrigée : réservée à une tentative terminée, pour la revue des erreurs. */
    public record QuestionReport(int position, String domainCode, String skillCode, String itemCode, ItemType type, String stem,
                                 Map<String, Object> payload, Map<String, Object> yourAnswer, Map<String, Object> correctAnswer,
                                 Double score, String explanationFr, int timeSpentSeconds, boolean locked) {}

    public record ReportView(AttemptSummary summary, int passScore, boolean estimated, List<ScoreBucket> domains,
                             List<ScoreBucket> skills, List<QuestionReport> questions,
                             boolean trial, int lockedCount) {}
}
