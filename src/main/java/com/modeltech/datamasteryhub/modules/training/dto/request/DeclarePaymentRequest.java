package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Le candidat déclare avoir payé")
public class DeclarePaymentRequest {

    @Schema(description = "WAVE, ORANGE_MONEY ou VIREMENT")
    @NotNull(message = "Le moyen de paiement est obligatoire")
    private PaymentMethod method;

    @Schema(description = "Référence de la transaction")
    @NotBlank(message = "La référence est obligatoire")
    @Size(max = 255)
    private String reference;
}
