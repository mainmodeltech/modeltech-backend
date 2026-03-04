package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRegistrationStatusRequest {

    @NotNull(message = "Le statut est obligatoire")
    private RegistrationStatus status;
}
