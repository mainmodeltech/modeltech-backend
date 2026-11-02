package com.modeltech.datamasteryhub.modules.simulator.dto;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrats de l'API d'administration de la banque de questions. Ces vues contiennent les <b>corrigés</b> : elles ne sont
 * servies qu'au personnel habilité. L'API apprenant (lot S2) utilisera des types distincts, sans aucun champ de correction.
 */
public final class SimulatorPayloads {

    private SimulatorPayloads() {}

    // ── Blueprint et couverture ─────────────────────────────────────

    public record SkillView(UUID id, String code, String name, int targetQuestions) {}

    public record DomainView(String code, String name, int weightMin, int weightMax, List<SkillView> skills) {}

    public record BlueprintView(UUID id, String certification, LocalDate versionDate, String label, int durationMinutes,
                                int questionsMin, int questionsMax, int passScore, List<DomainView> domains) {}

    public record CoverageRow(String domainCode, String skillCode, String skillName, int target,
                              long draft, long inReview, long approved, long published) {}

    // ── Questions ───────────────────────────────────────────────────

    /** Création ou modification du contenu d'une question. {@code skillCode} est obligatoire à la création. */
    public record ItemRequest(
            String skillCode,
            @NotNull ItemType type,
            @Min(1) @Max(3) int difficulty,
            @NotBlank @Size(max = 4000) String stemEn,
            @NotNull Map<String, Object> payload,
            @Size(max = 4000) String explanationFr) {}

    public record ItemSummary(String code, int versionNo, String domainCode, String skillCode, ItemType type, int difficulty,
                              ItemStatus status, String author, String stemPreview, LocalDateTime updatedAt,
                              boolean trialSample) {}

    public record ItemVersionView(String code, int versionNo, String domainCode, String skillCode, ItemType type, int difficulty,
                                  String stemEn, Map<String, Object> payload, String explanationFr, ItemStatus status,
                                  String blueprintLabel, String author, String reviewer, String reviewNote, String verifiedBy,
                                  LocalDateTime verifiedAt, String publisher, LocalDateTime createdAt, LocalDateTime publishedAt,
                                  LocalDateTime retiredAt, String retireReason, List<Integer> versions, boolean trialSample) {}

    public record TrialSampleRequest(boolean value) {}

    public record ReviewRequest(boolean approve, boolean verified, @Size(max = 2000) String note) {}

    public record RetireRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record ImportRequest(@NotEmpty @Size(max = 200) List<@Valid ItemRequest> items) {}

    public record ImportError(int index, String message) {}

    public record ImportResult(int created, List<String> codes, List<ImportError> errors) {}
}
