package com.modeltech.datamasteryhub.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailLogResponse {
    private UUID id;
    private String type;
    private String recipient;
    private String subject;
    /** SENT, FAILED ou SKIPPED. */
    private String status;
    private Integer attempts;
    private String error;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
