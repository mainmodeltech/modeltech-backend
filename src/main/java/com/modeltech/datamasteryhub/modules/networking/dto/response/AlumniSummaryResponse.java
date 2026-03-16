package com.modeltech.datamasteryhub.modules.networking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Vue résumée — utilisée dans les listes et comme membre d'un projet */
@Data
@Builder
public class AlumniSummaryResponse {
    private UUID    id;
    private String  name;
    private String  currentTitle;
    private String  currentPosition;
    private String  linkedinUrl;
    private String  photoUrl;
    private String  cohort;
    private String  bootcampTitle;
    private boolean published;
}

