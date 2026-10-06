package com.modeltech.datamasteryhub.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Mettre à jour un compte de back-office (tous champs optionnels)")
public class UpdateAdminUserRequest {

    @Size(max = 255)
    private String fullName;

    @Schema(description = "Remplace la liste des rôles")
    private List<String> roles;

    private UUID partnerId;

    private Boolean active;
}
