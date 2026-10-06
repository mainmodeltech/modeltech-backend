package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateDomainRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminDomainResponse;
import com.modeltech.datamasteryhub.modules.training.service.DomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/domains")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin - Domaines", description = "Gestion des domaines du catalogue")
@SecurityRequirement(name = "bearerAuth")
public class AdminDomainController {

    private final DomainService domainService;

    @PostMapping
    @Operation(summary = "Créer un domaine")
    public ResponseEntity<ApiResponse<AdminDomainResponse>> create(@Valid @RequestBody CreateDomainRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Domaine créé avec succès", domainService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Lister les domaines (paginé)")
    public ResponseEntity<ApiResponse<List<AdminDomainResponse>>> getAll(
            @PageableDefault(size = 20, sort = "displayOrder") Pageable pageable) {
        Page<AdminDomainResponse> page = domainService.findAllForAdmin(pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " domaine(s)", page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un domaine")
    public ResponseEntity<ApiResponse<AdminDomainResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Domaine trouvé", domainService.findByIdForAdmin(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un domaine")
    public ResponseEntity<ApiResponse<AdminDomainResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateDomainRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Domaine mis à jour", domainService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un domaine (soft delete) — refusé s'il contient des formations")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        domainService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
