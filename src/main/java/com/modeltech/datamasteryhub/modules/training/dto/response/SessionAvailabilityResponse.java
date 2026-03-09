package com.modeltech.datamasteryhub.modules.training.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Disponibilité d'une session de bootcamp")
public class SessionAvailabilityResponse {

    @Schema(description = "ID de la session")
    private UUID sessionId;

    @Schema(description = "Nom de la session")
    private String sessionName;

    @Schema(description = "ID du bootcamp")
    private UUID bootcampId;

    @Schema(description = "Titre du bootcamp")
    private String bootcampTitle;

    @Schema(description = "Capacité maximale")
    private Integer maxParticipants;

    @Schema(description = "Nombre de participants actuels")
    private Integer currentParticipants;

    @Schema(description = "Places restantes")
    private Integer remainingSpots;

    @Schema(description = "Pourcentage de places restantes (0-100)")
    private Integer remainingPercentage;

    @Schema(description = "Couleur recommandée pour l'affichage (green/yellow/red)")
    private String displayColor;

    @Schema(description = "Message formaté pour l'affichage")
    private String displayMessage;

    @Schema(description = "La session est complète")
    private Boolean isFull;

    @Schema(description = "La session accepte encore des inscriptions")
    private Boolean isOpenForRegistration;
}
