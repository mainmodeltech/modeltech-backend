package com.modeltech.datamasteryhub.modules.communication.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.enums.ContactType;
import com.modeltech.datamasteryhub.modules.communication.enums.RequesterType;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class ContactMessageResponseDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String company;
    private String subject;
    private String message;
    private ContactMessageStatus status;
    private String notes;
    private LocalDateTime createdAt;

    // ── Formulaires du site (ajouts additifs : omis quand ils sont nuls) ───
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ContactType type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RequesterType requesterType;

    /** Champs structurés des formulaires diagnostic / candidature partenaire. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> details;
}
