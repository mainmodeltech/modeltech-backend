package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.PayerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Accepter une candidature : calcule le montant, crée les échéances et envoie le lien de paiement. Tous les champs sont optionnels.")
public class AcceptRegistrationRequest {

    @Schema(description = "INDIVIDUAL (défaut) ou COMPANY (facture / bon de commande)")
    private PayerType payerType;

    @Schema(description = "Montant total négocié (XOF). Absent : calculé depuis le prix de la session/formation, l'early-bird et le code promo. Obligatoire si aucun prix numérique n'est renseigné.")
    @Min(value = 0, message = "Le montant doit être positif")
    private Long totalAmount;

    @Schema(description = "Échéances (1 à 12). Absent : un seul paiement. Montants absents : répartition égale ; sinon leur somme doit égaler le total.")
    @Size(max = 12, message = "12 échéances maximum")
    @Valid
    private List<Installment> installments;

    private String invoiceRef;
    private String purchaseOrderRef;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Installment {
        @Min(value = 0, message = "Le montant doit être positif")
        private Long amount;

        @NotNull(message = "La date d'échéance est obligatoire")
        private LocalDate dueDate;
    }
}
