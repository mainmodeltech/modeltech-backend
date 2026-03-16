package com.modeltech.datamasteryhub.modules.networking.controller;

import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniResponse;
import com.modeltech.datamasteryhub.modules.networking.service.AlumniService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/alumni")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin - Alumni", description = "Gestion des alumni")
public class AdminAlumniController {

    private final AlumniService alumniService;

    @PostMapping
    @Operation(summary = "Créer un alumni")
    public ResponseEntity<AlumniResponse> create(@Valid @RequestBody CreateAlumniRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alumniService.create(request));
    }

    @GetMapping
    @Operation(summary = "Lister tous les alumni (paginé)")
    public ResponseEntity<Page<AlumniResponse>> getAll(
            @PageableDefault(size = 20, sort = "displayOrder") Pageable pageable) {
        return ResponseEntity.ok(alumniService.findAllForAdmin(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un alumni par ID")
    public ResponseEntity<AlumniResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(alumniService.findByIdForAdmin(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un alumni")
    public ResponseEntity<AlumniResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAlumniRequest request) {
        return ResponseEntity.ok(alumniService.update(id, request));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader la photo d'un alumni (MinIO)")
    public ResponseEntity<AlumniResponse> uploadPhoto(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(alumniService.uploadPhoto(id, file));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un alumni (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        alumniService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
