package com.modeltech.datamasteryhub.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class GoogleLoginRequest {

    /** « credential » renvoyé par Google Identity Services (ID token JWT). */
    @NotBlank(message = "Le jeton Google est obligatoire")
    @Size(max = 4096)
    @ToString.Exclude
    private String credential;
}
