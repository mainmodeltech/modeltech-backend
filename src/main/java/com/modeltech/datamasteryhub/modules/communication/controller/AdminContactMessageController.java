package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.modules.communication.dto.request.UpdateMessageRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.ContactMessageResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessageStatus;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/contact-messages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - ContactMessage", description = "CRUD complet pour la gestion des messages de contacts")
public class AdminContactMessageController {

    private final ContactMessageService contactMessageService;

    @GetMapping
    @Operation(summary = "Lister tous les message de contact (hors supprimés)")
    public List<ContactMessageResponseDTO> list() {
        List<ContactMessageResponseDTO> messages = contactMessageService.getAllMessages();
        log.info("Récupération de {} messages de contact", messages);
        return contactMessageService.getAllMessages();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Afficher un message de contact (hors supprimés) par ID")
    public ResponseEntity<ContactMessageResponseDTO> getById(
            @PathVariable UUID id
    ) {

        log.info("Récupération du messages de contact pour l'ID {}", id);
        return ResponseEntity.ok(contactMessageService.findByIdForAdmin(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un message par ID")
    @ApiResponse(responseCode = "200", description = "Message trouvé")
    @ApiResponse(responseCode = "404", description = "Message introuvable")
    public ContactMessageResponseDTO update(
            @PathVariable UUID id,
            @RequestParam UpdateMessageRequestDTO updateContactMessage) {
        return contactMessageService.updateMessage(id, updateContactMessage);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Modifier le status d'un message par ID")
    @ApiResponse(responseCode = "200", description = "Message trouvé")
    @ApiResponse(responseCode = "404", description = "Message introuvable")
    public ContactMessageResponseDTO updateStatus(
            @PathVariable UUID id,
            @RequestParam ContactMessageStatus status) {
        return contactMessageService.updateStatus(id, status);
    }
}
