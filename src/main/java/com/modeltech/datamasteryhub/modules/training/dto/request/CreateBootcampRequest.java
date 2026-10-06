package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampCertification;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampOutcome;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampProfile;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampTool;
import com.modeltech.datamasteryhub.modules.training.entity.content.CurriculumWeek;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.FormationFormat;
import com.modeltech.datamasteryhub.modules.training.enums.FormationLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateBootcampRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String description;
    private String duration;
    private String audience;
    private String prerequisites;
    private String price;
    private List<String> benefits;
    private String category = "data";
    private String tag;
    private String iconName;
    private Boolean featured = false;
    private Boolean published = true;
    private Integer displayOrder = 0;

    // ── Contenu riche de la fiche formation (facultatif) ──────────────
    private String tagline;
    private String colorKey;
    private List<BootcampProfile> profiles;
    private List<BootcampTool> tools;
    private List<CurriculumWeek> curriculum;
    private List<BootcampOutcome> outcomes;
    private BootcampCertification certification;

    // ── Catalogue par domaines (facultatif) ───────────────────────────
    /** Identifiant d'URL ; généré depuis le titre si absent. */
    private String slug;
    private UUID domainId;
    /** PARTNER impose un partnerId ; INTERNAL n'a pas de partenaire. */
    private DeliveredBy deliveredBy = DeliveredBy.INTERNAL;
    private UUID partnerId;
    private FormationLevel level;
    private FormationFormat format;
    private String certificationPrep;
    private List<String> targetRoles;
    private List<UUID> relatedFormationIds;
}
