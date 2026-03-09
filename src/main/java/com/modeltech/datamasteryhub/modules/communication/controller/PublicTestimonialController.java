package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.TestimonialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints publics — aucune authentification requise.
 * Utilisé par le frontend pour afficher les témoignages sur la landing page et la page Alumni.
 */
@RestController
@RequestMapping("/api/v1/testimonials")
@RequiredArgsConstructor
public class PublicTestimonialController {

    private final TestimonialService testimonialService;

    /**
     * GET /api/v1/testimonials/published
     * Retourne la liste des témoignages publiés, triés par displayOrder.
     */
    @GetMapping("/published")
    public ResponseEntity<ApiResponse<List<TestimonialResponseDTO>>> getPublished() {
        List<TestimonialResponseDTO> data = testimonialService.getPublished();
        return ResponseEntity.ok(
                ApiResponse.ok("Témoignages publiés récupérés avec succès", data)
        );
    }
}
