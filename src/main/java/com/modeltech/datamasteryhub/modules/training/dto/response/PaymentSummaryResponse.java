package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Résumé des paiements d'une inscription (cartes du Kanban des candidatures). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentSummaryResponse {
    private int installmentCount;
    private int confirmedCount;
    private long paidAmount;
    private LocalDate nextDueDate;
    /** Envoi du premier lien de paiement. */
    private LocalDateTime linkSentAt;
    private LocalDateTime lastReminderAt;
    /** Déclaration en attente de vérification (le cas échéant). */
    private String declaredMethod;
    private String declaredReference;
    private Boolean declaredHasProof;
}
