package com.modeltech.datamasteryhub.modules.auth.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Soit {@code token} (lien reçu), soit {@code email} + {@code code} (6 chiffres saisis). */
@Data
public class PasswordlessVerifyRequest {

    @Size(max = 200)
    private String token;
    @Size(max = 255)
    private String email;
    @Size(max = 12)
    private String code;
}
