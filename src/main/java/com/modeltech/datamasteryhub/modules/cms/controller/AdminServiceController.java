package com.modeltech.datamasteryhub.modules.cms.controller;

import com.modeltech.datamasteryhub.modules.cms.dto.request.CreateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.request.UpdateServiceRequest;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceResponse;
import com.modeltech.datamasteryhub.modules.cms.dto.response.ServiceSummaryResponse;
import com.modeltech.datamasteryhub.modules.cms.service.ServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/services")
@RequiredArgsConstructor
@Tag(name = "Admin - Services", description = "CRUD complet pour la gestion des services")
public class AdminServiceController {

    private final ServiceService serviceService;

    @PostMapping
    @Operation(summary = "Creer un service")
    public ResponseEntity<ServiceResponse> create(
            @Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceService.create(request));
    }

    @GetMapping
    @Operation(summary = "Lister tous les services (admin, pagine)")
    public ResponseEntity<Page<ServiceSummaryResponse>> getAll(
            @PageableDefault(size = 20, sort = "displayOrder") Pageable pageable) {
        return ResponseEntity.ok(serviceService.findAllForAdmin(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recuperer un service par ID")
    public ResponseEntity<ServiceResponse> getById(
            @Parameter(description = "UUID du service") @PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.findByIdForAdmin(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre a jour un service")
    public ResponseEntity<ServiceResponse> update(
            @Parameter(description = "UUID du service") @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(serviceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un service (soft delete)")
    public ResponseEntity<Void> delete(
            @Parameter(description = "UUID du service") @PathVariable UUID id) {
        serviceService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
