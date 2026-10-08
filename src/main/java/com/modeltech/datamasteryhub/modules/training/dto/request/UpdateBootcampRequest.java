package com.modeltech.datamasteryhub.modules.training.dto.request;

import jakarta.validation.constraints.Min;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampCertification;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampOutcome;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampProfile;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampTool;
import com.modeltech.datamasteryhub.modules.training.entity.content.CurriculumWeek;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.FormationFormat;
import com.modeltech.datamasteryhub.modules.training.enums.FormationLevel;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateBootcampRequest {
    private String title;
    private String description;
    private String duration;
    private String audience;
    private String prerequisites;
    private String price;

    @Min(value = 0, message = "Le montant doit être positif")
    private Long priceAmount;

    @jakarta.validation.constraints.Pattern(regexp = "^[A-Z0-9]{2,6}$", message = "2 à 6 lettres majuscules ou chiffres")
    private String certificateCode;
    private List<String> benefits;
    private String category;
    private String tag;
    private String iconName;
    private Boolean featured;
    private Boolean published;
    private Integer displayOrder;

    // ── Contenu riche de la fiche formation (facultatif) ──────────────
    private String tagline;
    private String colorKey;
    private List<BootcampProfile> profiles;
    private List<BootcampTool> tools;
    private List<CurriculumWeek> curriculum;
    private List<BootcampOutcome> outcomes;
    private BootcampCertification certification;

    // ── Catalogue par domaines (facultatif) ───────────────────────────
    private String slug;
    private UUID domainId;
    /** INTERNAL efface le partenaire ; PARTNER impose un partnerId. */
    private DeliveredBy deliveredBy;
    private UUID partnerId;
    private FormationLevel level;
    private FormationFormat format;
    private String certificationPrep;
    private List<String> targetRoles;
    /** Remplace la liste des formations liées (liste vide = aucune). */
    private List<UUID> relatedFormationIds;
}