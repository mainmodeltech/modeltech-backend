package com.modeltech.datamasteryhub.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TestEmailResponse {
    private boolean sent;
    /** Destinataire effectif (différent de l'adresse demandée si une redirection est active). */
    private String deliveredTo;
    private String message;
}
