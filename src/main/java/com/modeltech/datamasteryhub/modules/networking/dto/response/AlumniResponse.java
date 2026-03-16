package com.modeltech.datamasteryhub.modules.networking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Vue complète — utilisée dans la fiche détail admin */
@Data
@Builder
public class AlumniResponse {
    private UUID          id;
    private UUID          registrationId;
    private String        name;
    private String        email;
    private String        phone;
    private String        currentTitle;
    private String        currentPosition;
    private String        linkedinUrl;
    private String        photoUrl;
    private String        cohort;
    private Integer       year;
    private String        bootcampTitle;
    private boolean       published;
    private int           displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
