package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/contact-messages")
@RequiredArgsConstructor
@Slf4j
//@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - ContactMessage", description = "Gestion des messages de contact")
public class AdminContactMessageController {

    private final ContactMessageService contactMessageService;

    /**
     * GET /api/v1/admin/contact-messages?page=0&size=20
     * Liste paginée de tous les messages, triée du plus récent au plus ancien.
     */
    @GetMapping
    @Operation(summary = "Lister les messages de contact (paginé)")
    public ResponseEntity<ApiResponse<java.util.List<ContactMessageResponseDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ContactMessageResponseDTO> result = contactMessageService.getAllMessages(page, size);
        return ResponseEntity.ok(
                ApiResponse.page("Messages récupérés avec succès", result)
        );
    }

    /**
     * GET /api/v1/admin/contact-messages/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Afficher un message par ID")
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok("Message récupéré avec succès", contactMessageService.findByIdForAdmin(id))
        );
    }

    /**
     * PUT /api/v1/admin/contact-messages/{id}
     * Modifie le contenu d'un message.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Modifier un message par ID")
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMessageRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.ok("Message mis à jour avec succès", contactMessageService.updateMessage(id, dto))
        );
    }

    /**
     * PUT /api/v1/admin/contact-messages/{id}/status
     * Change le statut d'un message (unread → read → archived...).
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Modifier le statut d'un message")
    public ResponseEntity<ApiResponse<ContactMessageResponseDTO>> updateStatus(
            @PathVariable UUID id,
            @RequestParam ContactMessageStatus status) {
        ContactMessageResponseDTO updated = contactMessageService.updateStatus(id, status);
        String msg = switch (status) {
            case read -> "Message marqué comme lu";
            case archived -> "Message archivé";
            default -> "Statut mis à jour";
        };
        return ResponseEntity.ok(ApiResponse.ok(msg, updated));
    }
}