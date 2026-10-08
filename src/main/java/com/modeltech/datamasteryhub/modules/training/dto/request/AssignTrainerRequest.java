package com.modeltech.datamasteryhub.modules.training.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Confier une session à un formateur (null : retirer le formateur)")
public class AssignTrainerRequest {

    @Schema(description = "Identifiant d'un compte du back-office ayant le rôle TRAINER")
    private UUID trainerId;
}
