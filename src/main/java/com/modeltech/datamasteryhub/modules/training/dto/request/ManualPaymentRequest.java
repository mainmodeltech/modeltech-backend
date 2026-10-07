package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Enregistrer un paiement reçu hors du site (virement, espèces, facture entreprise) sur une échéance")
public class ManualPaymentRequest {

    @Schema(description = "Échéance concernée ; absent : la première non encore payée")
    @Min(1)
    private Integer installmentNumber;

    @NotNull(message = "Le moyen de paiement est obligatoire")
    private PaymentMethod method;

    @Size(max = 255)
    private String reference;

    @Size(max = 255)
    private String invoiceRef;

    @Size(max = 255)
    private String purchaseOrderRef;

    @Size(max = 2000)
    private String notes;
}
