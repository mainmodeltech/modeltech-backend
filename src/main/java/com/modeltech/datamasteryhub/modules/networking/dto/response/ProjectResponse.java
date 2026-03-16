package com.modeltech.datamasteryhub.modules.networking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Vue complète d'un projet avec membres et screenshots */
@Data
@Builder
public class ProjectResponse {
    private UUID                          id;
    private String                        title;
    private String                        description;
    private List<String>                  toolsTechnologies;
    private String                        accessLink;
    private String                        coverImageUrl;
    private String                        cohort;
    private Integer                       year;
    private boolean                       published;
    private int                           displayOrder;
    private List<ProjectMemberResponse>   members;
    private List<ProjectScreenshotResponse> screenshots;
    private LocalDateTime                 createdAt;
    private LocalDateTime                 updatedAt;
}
