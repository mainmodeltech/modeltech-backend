package com.modeltech.datamasteryhub.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Paiements et inscriptions vus par l'apprenant (JSON brut, comme {@code /learner/**}). */
public final class LearnerBillingPayloads {

    private LearnerBillingPayloads() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentItem {
        private String id;
        private String registrationId;
        private String formationTitle;
        private String sessionName;
        private int installmentNumber;
        private int installmentCount;
        private long amount;
        private String currency;
        /** Total de l'inscription (toutes échéances). */
        private Long totalAmount;
        private LocalDate dueDate;
        /** PENDING, DECLARED, CONFIRMED ou REFUNDED. */
        private String status;
        private String method;
        private LocalDateTime confirmedAt;
        /** Lien de la page de paiement : seulement tant que l'échéance est à régler ou à vérifier. */
        private String payUrl;
        /** PDF du reçu : seulement pour une échéance confirmée. */
        private String receiptUrl;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EnrollmentItem {
        private String formationId;
        private String title;
        private String sessionName;
        /** ACTIVE ou COMPLETED. */
        private String status;
        private LocalDate accessStartsAt;
        private LocalDate accessEndsAt;
        private String trainerName;
        private String attestationUrl;
    }
}
