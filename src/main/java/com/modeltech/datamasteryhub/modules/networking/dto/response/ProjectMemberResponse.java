package com.modeltech.datamasteryhub.modules.networking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ProjectMemberResponse {
    private UUID                id;
    private AlumniSummaryResponse alumni;
    private String              role;
    private int                 displayOrder;
}
