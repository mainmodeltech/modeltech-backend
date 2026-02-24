package com.modeltech.datamasteryhub.modules.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceSummaryResponse {

    private UUID id;
    private String title;
    private String description;
    private String iconName;
    private List<String> features;
    private String duration;
    private Integer displayOrder;
    private boolean published;
}
