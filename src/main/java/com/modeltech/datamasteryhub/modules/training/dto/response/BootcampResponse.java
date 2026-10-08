package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampCertification;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampOutcome;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampProfile;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampTool;
import com.modeltech.datamasteryhub.modules.training.entity.content.CurriculumWeek;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.FormationFormat;
import com.modeltech.datamasteryhub.modules.training.enums.FormationLevel;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BootcampResponse {
    private UUID id;
    private String title;
    private String description;
    private String duration;
    private String audience;
    private String prerequisites;
    private String price;
    private Long priceAmount;
    private String certificateCode;
    private String currency;
    private List<String> benefits;
    private String category;
    private String tag;
    private String iconName;
    private Boolean featured;
    private Boolean published;
    private Integer displayOrder;

    // ── Contenu riche de la fiche formation (nullable — voir docs/redesign) ──
    private String tagline;
    private String colorKey;
    private List<BootcampProfile> profiles;
    private List<BootcampTool> tools;
    private List<CurriculumWeek> curriculum;
    private List<BootcampOutcome> outcomes;
    private BootcampCertification certification;
    private BootcampTestimonialResponse testimonial;

    // ── Catalogue par domaines (contrat Formation de formation.type.ts) ──
    private String slug;
    private UUID domainId;
    private DomainResponse domain;
    private DeliveredBy deliveredBy;
    private UUID partnerId;
    private PartnerResponse partner;
    private FormationLevel level;
    private FormationFormat format;
    private List<String> targetRoles;
    private String certificationPrep;
    private List<UUID> relatedFormationIds;

    // Session mise en avant (la prochaine session ouverte)
    private BootcampSessionResponse nextSession;

    // Toutes les sessions (pour la page détail)
    private List<BootcampSessionResponse> sessions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
