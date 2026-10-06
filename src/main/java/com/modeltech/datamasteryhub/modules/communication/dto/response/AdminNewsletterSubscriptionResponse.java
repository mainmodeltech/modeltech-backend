package com.modeltech.datamasteryhub.modules.communication.dto.response;

import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Vue admin d'un abonné — volontairement sans aucun jeton. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminNewsletterSubscriptionResponse {
    private UUID id;
    private String email;
    private NewsletterStatus status;
    private String source;
    private LocalDateTime confirmedAt;
    private LocalDateTime unsubscribedAt;
    private LocalDateTime createdAt;
}
