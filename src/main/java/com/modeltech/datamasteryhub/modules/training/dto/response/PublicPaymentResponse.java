package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Page « lien de paiement » : accessible avec le seul jeton, donc aucune donnée personnelle sensible. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicPaymentResponse {

    private String firstName;
    private String bootcampTitle;
    private String sessionName;
    private LocalDate sessionStartDate;

    private Long amount;
    private String currency;
    private Integer installmentNumber;
    private Integer installmentCount;
    private LocalDate dueDate;
    private PaymentStatus status;
    private PaymentMethod method;
    private String reference;
    private Boolean hasProof;
    private String rejectionReason;

    /** Montant total de l'inscription (toutes échéances). */
    private Long totalAmount;
    private List<ScheduleItem> schedule;

    /** Où payer : numéro Wave / Orange Money et moyens acceptés. */
    private PayTo payTo;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ScheduleItem {
        private Integer installmentNumber;
        private Long amount;
        private LocalDate dueDate;
        private PaymentStatus status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PayTo {
        private String phone;
        private List<PaymentMethod> methods;
    }
}
