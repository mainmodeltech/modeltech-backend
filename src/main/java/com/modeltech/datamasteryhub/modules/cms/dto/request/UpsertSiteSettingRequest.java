package com.modeltech.datamasteryhub.modules.cms.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Créer ou remplacer un contenu du site")
public class UpsertSiteSettingRequest {

    @Schema(description = "Valeur JSON libre (objet, tableau, texte, nombre) — 20 Ko max. Pour retirer un contenu, utiliser DELETE.")
    @NotNull(message = "La valeur est obligatoire")
    private JsonNode value;
}
