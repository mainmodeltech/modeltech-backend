package com.modeltech.datamasteryhub.modules.networking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ProjectScreenshotResponse {
    private UUID   id;
    private String photoUrl;
    private String caption;
    private int    displayOrder;
}
