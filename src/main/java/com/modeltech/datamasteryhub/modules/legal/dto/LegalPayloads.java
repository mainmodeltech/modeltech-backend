package com.modeltech.datamasteryhub.modules.legal.dto;

import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.LegalContext;
import com.modeltech.datamasteryhub.modules.legal.LegalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Textes légaux, exigences d'acceptation et preuves. */
public final class LegalPayloads {

    private LegalPayloads() {}

    // ── Public ───────────────────────────────────────────────────────

    /** @param path adresse de la page publique du texte (ex. « /cgu ») */
    public record DocumentRef(UUID id, LegalCode code, String title, int version, LocalDateTime publishedAt, String path) {}

    public record PublicDocument(DocumentRef document, String body) {}

    /** Les textes à accepter (publiés) pour un acte ; liste vide = rien à accepter pour l'instant. */
    public record Requirements(LegalContext context, List<DocumentRef> documents) {}

    /** Acceptation donnée à un acte déjà identifié (page de paiement, espace apprenant). */
    public record AcceptRequest(@NotEmpty List<UUID> documents) {}

    // ── Administration ───────────────────────────────────────────────

    public record AdminDocument(UUID id, LegalCode code, String title, int version, LegalStatus status, String body, String changeSummary,
                                String counselValidatedBy, LocalDate counselValidatedOn, LocalDateTime publishedAt, String publishedBy,
                                LocalDateTime createdAt, LocalDateTime updatedAt, boolean hasPlaceholders) {}

    /** Un texte vu de l'administration : version en vigueur, brouillon en cours et historique des versions. */
    public record AdminOverview(LegalCode code, String label, String path, AdminDocument published, AdminDocument draft, List<AdminDocument> history) {}

    public record SaveDraft(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 200_000) String body, @Size(max = 2000) String changeSummary) {}

    /** La publication exige la validation du conseil juridique (qui, quand) et un résumé de ce qui change. */
    public record Publish(@NotBlank @Size(max = 255) String counselValidatedBy, @NotNull @PastOrPresent LocalDate counselValidatedOn,
                          @NotBlank @Size(max = 2000) String changeSummary) {}

    public record AcceptanceView(UUID id, LegalCode code, int version, String contentHash, String context, String subjectType, String subjectRef,
                                 String email, String fullName, LocalDateTime acceptedAt, String ipAddress) {}
}
