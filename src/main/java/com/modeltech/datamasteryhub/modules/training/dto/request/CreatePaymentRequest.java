package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Enregistrer un paiement sur une inscription")
public class CreatePaymentRequest {

    @NotNull(message = "Le montant est obligatoire")
    @Min(value = 1, message = "Le montant doit etre positif")
    private Integer amount;

    @NotNull(message = "La date de paiement est obligatoire")
    private LocalDate paymentDate;

    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod paymentMethod;

    /** Reference de la transaction (Wave, OM, virement, etc.) */
    private String reference;

    /** Notes libres */
    private String notes;
}
