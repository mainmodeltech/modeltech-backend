package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.request.*;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSessionResponse;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/bootcamps")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Bootcamps (Admin)", description = "Gestion des bootcamps — authentification requise")
@SecurityRequirement(name = "bearerAuth")
public class AdminBootcampController {

    private final BootcampService bootcampService;

    // ── Bootcamps ────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Liste tous les bootcamps (admin)")
    public ResponseEntity<List<BootcampResponse>> findAll() {
        return ResponseEntity.ok(bootcampService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un bootcamp avec toutes ses sessions (admin)")
    public ResponseEntity<BootcampResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(bootcampService.findByIdForAdmin(id));
    }

    @PostMapping
    @Operation(summary = "Créer un bootcamp")
    public ResponseEntity<BootcampResponse> create(@Valid @RequestBody CreateBootcampRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bootcampService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un bootcamp")
    public ResponseEntity<BootcampResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateBootcampRequest request
    ) {
        return ResponseEntity.ok(bootcampService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un bootcamp (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bootcampService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-published")
    @Operation(summary = "Publier / dépublier un bootcamp")
    public ResponseEntity<BootcampResponse> togglePublished(@PathVariable UUID id) {
        return ResponseEntity.ok(bootcampService.togglePublished(id));
    }

    // ── Sessions ─────────────────────────────────────────────────────

    @GetMapping("/{bootcampId}/sessions")
    @Operation(summary = "Liste toutes les sessions d'un bootcamp (admin)")
    public ResponseEntity<List<BootcampSessionResponse>> findSessions(@PathVariable UUID bootcampId) {
        return ResponseEntity.ok(bootcampService.findSessionsByBootcamp(bootcampId, true));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Détail d'une session")
    public ResponseEntity<BootcampSessionResponse> findSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(bootcampService.findSessionById(sessionId));
    }

    @PostMapping("/{bootcampId}/sessions")
    @Operation(summary = "Créer une session pour un bootcamp")
    public ResponseEntity<BootcampSessionResponse> createSession(
            @PathVariable UUID bootcampId,
            @Valid @RequestBody CreateBootcampSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bootcampService.createSession(bootcampId, request));
    }

    @PutMapping("/sessions/{sessionId}")
    @Operation(summary = "Mettre à jour une session")
    public ResponseEntity<BootcampSessionResponse> updateSession(
            @PathVariable UUID sessionId,
            @RequestBody UpdateBootcampSessionRequest request
    ) {
        return ResponseEntity.ok(bootcampService.updateSession(sessionId, request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Supprimer une session (soft delete)")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        bootcampService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/sessions/{sessionId}/toggle-featured")
    @Operation(summary = "Mettre en avant / retirer la mise en avant d'une session")
    public ResponseEntity<BootcampSessionResponse> toggleFeatured(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(bootcampService.toggleSessionFeatured(sessionId));
    }
}