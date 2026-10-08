package com.modeltech.datamasteryhub.modules.cms.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SiteSettingResponse {
    private String key;
    private JsonNode value;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
