package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdatePartnerRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPartnerResponse;
import com.modeltech.datamasteryhub.modules.training.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/partners")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin - Partenaires", description = "Gestion des partenaires formateurs")
@SecurityRequirement(name = "bearerAuth")
public class AdminPartnerController {

    private final PartnerService partnerService;

    @PostMapping
    @Operation(summary = "Créer un partenaire")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> create(@Valid @RequestBody CreatePartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Partenaire créé avec succès", partnerService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Lister les partenaires (paginé)")
    public ResponseEntity<ApiResponse<List<AdminPartnerResponse>>> getAll(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<AdminPartnerResponse> page = partnerService.findAllForAdmin(pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " partenaire(s)", page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un partenaire")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Partenaire trouvé", partnerService.findByIdForAdmin(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un partenaire")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePartnerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Partenaire mis à jour", partnerService.update(id, request)));
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader le logo d'un partenaire (MinIO)")
    public ResponseEntity<ApiResponse<AdminPartnerResponse>> uploadLogo(
            @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Logo mis à jour", partnerService.uploadLogo(id, file)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un partenaire (soft delete) — refusé s'il dispense des formations")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partnerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
