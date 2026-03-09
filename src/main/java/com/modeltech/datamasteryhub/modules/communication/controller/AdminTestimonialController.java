package com.modeltech.datamasteryhub.modules.communication.controller;


import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.TestimonialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints admin — requiert le rôle ADMIN.
 * CRUD complet sur les témoignages.
 */
@RestController
@RequestMapping("/api/v1/admin/testimonials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTestimonialController {

    private final TestimonialService testimonialService;

    /**
     * GET /api/v1/admin/testimonials
     * Retourne tous les témoignages (publiés + non publiés).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TestimonialResponseDTO>>> getAll() {
        List<TestimonialResponseDTO> data = testimonialService.getAll();
        return ResponseEntity.ok(
                ApiResponse.ok("Liste des témoignages récupérée avec succès", data)
        );
    }

    /**
     * GET /api/v1/admin/testimonials/{id}
     * Retourne un témoignage par son id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TestimonialResponseDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok("Témoignage récupéré avec succès", testimonialService.getById(id))
        );
    }

    /**
     * POST /api/v1/admin/testimonials
     * Crée un nouveau témoignage.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TestimonialResponseDTO>> create(
            @Valid @RequestBody TestimonialRequestDTO request) {
        TestimonialResponseDTO created = testimonialService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Témoignage créé avec succès", created));
    }

    /**
     * PUT /api/v1/admin/testimonials/{id}
     * Met à jour un témoignage existant.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TestimonialResponseDTO>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TestimonialRequestDTO request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Témoignage mis à jour avec succès", testimonialService.update(id, request))
        );
    }

    /**
     * DELETE /api/v1/admin/testimonials/{id}
     * Soft-delete un témoignage.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        testimonialService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Témoignage supprimé avec succès", null)
        );
    }

    /**
     * PATCH /api/v1/admin/testimonials/{id}/toggle-published
     * Bascule le statut published (publié ↔ non publié).
     */
    @PatchMapping("/{id}/toggle-published")
    public ResponseEntity<ApiResponse<TestimonialResponseDTO>> togglePublished(@PathVariable UUID id) {
        TestimonialResponseDTO updated = testimonialService.togglePublished(id);
        String message = Boolean.TRUE.equals(updated.getPublished())
                ? "Témoignage publié avec succès"
                : "Témoignage dépublié avec succès";
        return ResponseEntity.ok(ApiResponse.ok(message, updated));
    }
}