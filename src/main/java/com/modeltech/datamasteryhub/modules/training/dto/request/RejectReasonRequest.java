package com.modeltech.datamasteryhub.modules.training.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Motif d'un refus (candidature ou paiement)")
public class RejectReasonRequest {

    @NotBlank(message = "Le motif est obligatoire")
    @Size(max = 1000)
    private String reason;
}
