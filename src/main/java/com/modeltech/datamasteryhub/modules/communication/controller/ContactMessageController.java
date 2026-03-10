package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.request.ContactMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contact-messages")
@RequiredArgsConstructor
public class ContactMessageController {

    private final ContactMessageService contactMessageService;

    /**
     * POST /api/v1/contact-messages
     * Soumet un message de contact — déclenche email + Slack.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> create(
            @Valid @RequestBody ContactMessageRequestDTO dto) {
        ContactMessageResponseDTO created = contactMessageService.saveMessage(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message envoyé avec succès. Nous vous répondrons dans les plus brefs délais.", created));
    }
}