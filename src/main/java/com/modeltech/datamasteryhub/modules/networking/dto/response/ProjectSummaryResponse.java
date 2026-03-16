package com.modeltech.datamasteryhub.modules.networking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Vue résumée pour les listes */
@Data
@Builder
public class ProjectSummaryResponse {
    private UUID                        id;
    private String                      title;
    private String                      description;
    private List<String>                toolsTechnologies;
    private String                      accessLink;
    private String                      coverImageUrl;
    private String                      cohort;
    private Integer                     year;
    private List<ProjectMemberResponse> members;
    private boolean                     published;
}
