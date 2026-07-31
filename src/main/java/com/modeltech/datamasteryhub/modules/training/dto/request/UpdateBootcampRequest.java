package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampCertification;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampOutcome;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampProfile;
import com.modeltech.datamasteryhub.modules.training.entity.content.BootcampTool;
import com.modeltech.datamasteryhub.modules.training.entity.content.CurriculumWeek;
import lombok.Data;
import java.util.List;

@Data
public class UpdateBootcampRequest {
    private String title;
    private String description;
    private String duration;
    private String audience;
    private String prerequisites;
    private String price;
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
}