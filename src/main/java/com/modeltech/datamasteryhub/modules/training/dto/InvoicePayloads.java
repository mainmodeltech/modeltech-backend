package com.modeltech.datamasteryhub.modules.training.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Factures des entreprises. */
public final class InvoicePayloads {

    private InvoicePayloads() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Émettre la facture d'une inscription acceptée. Tous les champs sont facultatifs.")
    public static class CreateRequest {
        @Schema(description = "Raison sociale ; absent : l'entreprise de l'inscription, sinon le nom du candidat")
        @Size(max = 255)
        private String buyerName;

        @Size(max = 255)
        private String buyerContact;

        @Email
        @Size(max = 255)
        private String buyerEmail;

        @Size(max = 500)
        private String buyerAddress;

        @Size(max = 255)
        private String purchaseOrderRef;

        @Schema(description = "Date limite de règlement ; absente : la première échéance, sinon le délai par défaut")
        private LocalDate dueDate;

        @Size(max = 2000)
        private String notes;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CancelRequest {
        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 1000)
        private String reason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendRequest {
        @Schema(description = "Destinataire ; absent : l'adresse de facturation de la facture, sinon celle du candidat")
        @Email
        private String to;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceResponse {
        private UUID id;
        private String number;
        private UUID registrationId;
        private String status;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private String buyerName;
        private String buyerContact;
        private String buyerEmail;
        private String purchaseOrderRef;
        private String description;
        private Long unitAmount;
        private Long discountAmount;
        private Long totalExclVat;
        private Long vatAmount;
        private Long total;
        private String currency;
        private LocalDateTime cancelledAt;
        private String cancelledReason;
        /** Chemin du PDF sur l'API. */
        private String pdfPath;
    }
}
