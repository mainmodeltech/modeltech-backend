package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/v1/admin/bootcamps")
@RequiredArgsConstructor
@Tag(name = "Admin - Bootcamps", description = "CRUD complet pour la gestion des bootcamps")
public class AdminBootcampController {

    private final BootcampService bootcampService;

    @PostMapping
    @Operation(summary = "Créer un nouveau bootcamp")
    @ApiResponse(responseCode = "201", description = "Bootcamp créé avec succès")
    public ResponseEntity<BootcampResponse> create(@Valid @RequestBody CreateBootcampRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bootcampService.create(request));
    }

    @GetMapping
    @Operation(summary = "Lister tous les bootcamps (hors supprimés)")
    public ResponseEntity<Page<BootcampSummaryResponse>> getAll(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(bootcampService.findAllForAdmin(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un bootcamp par ID")
    @ApiResponse(responseCode = "200", description = "Bootcamp trouvé")
    @ApiResponse(responseCode = "404", description = "Bootcamp introuvable")
    public ResponseEntity<BootcampResponse> getById(
            @Parameter(description = "UUID du bootcamp") @PathVariable UUID id) {
        return ResponseEntity.ok(bootcampService.findByIdForAdmin(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un bootcamp")
    @ApiResponse(responseCode = "200", description = "Bootcamp modifié avec succès")
    @ApiResponse(responseCode = "404", description = "Bootcamp introuvable")
    public ResponseEntity<BootcampResponse> update(
            @Parameter(description = "UUID du bootcamp") @PathVariable UUID id,
            @Valid @RequestBody UpdateBootcampRequest request) {
        return ResponseEntity.ok(bootcampService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un bootcamp (soft delete)")
    @ApiResponse(responseCode = "204", description = "Bootcamp supprimé avec succès")
    @ApiResponse(responseCode = "404", description = "Bootcamp introuvable")
    public ResponseEntity<Void> delete(
            @Parameter(description = "UUID du bootcamp") @PathVariable UUID id) {
        bootcampService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
