package com.modeltech.datamasteryhub.modules.cms.controller;

import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceResponse;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceSummaryResponse;
import com.modeltech.datamasteryhub.modules.cms.service.ServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Tag(name = "Public - Services", description = "Endpoints publics en lecture seule pour les services")
public class PublicServiceController {

    private final ServiceService serviceService;

    @GetMapping
    @Operation(summary = "Lister les services publies (liste plate, triee par displayOrder)")
    public ResponseEntity<List<ServiceSummaryResponse>> getAllPublished() {
        return ResponseEntity.ok(serviceService.findAllPublished());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recuperer un service publie par ID")
    public ResponseEntity<ServiceResponse> getPublishedById(
            @Parameter(description = "UUID du service") @PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.findPublishedById(id));
    }
}
