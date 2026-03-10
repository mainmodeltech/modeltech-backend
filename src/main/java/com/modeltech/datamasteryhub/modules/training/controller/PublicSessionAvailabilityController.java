package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.response.SessionAvailabilityResponse;
import com.modeltech.datamasteryhub.modules.training.service.BootcampSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public - Sessions", description = "Endpoints publics pour les sessions de bootcamp")
public class PublicSessionAvailabilityController {

    private final BootcampSessionService bootcampSessionService;

    @GetMapping("/{sessionId}/availability")
    @Operation(
            summary = "Récupérer le nombre de places restantes",
            description = "Retourne les places disponibles, le pourcentage restant et une couleur recommandée pour l'affichage"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Disponibilité récupérée avec succès",
                    content = @Content(schema = @Schema(implementation = SessionAvailabilityResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Session non trouvée ou non publiée",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "ID de session invalide",
                    content = @Content
            )
    })
    public ResponseEntity<SessionAvailabilityResponse> getSessionAvailability(
            @Parameter(description = "ID de la session", required = true)
            @PathVariable UUID sessionId) {

        log.info("GET /api/v1/sessions/{}/availability - Récupération des places restantes", sessionId);

        SessionAvailabilityResponse availability = bootcampSessionService.getSessionAvailability(sessionId);
        return ResponseEntity.ok(availability);
    }
}
