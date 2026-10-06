package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * Session « à plat » enrichie de sa formation, pour la vue calendrier du catalogue
 * (contrat FlatSession de formation.type.ts).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FormationSessionResponse extends BootcampSessionResponse {
    private UUID formationId;
    private String formationSlug;
    private String formationTitle;
    private String domainName;
    private DeliveredBy deliveredBy;
    private String partnerName;
}
