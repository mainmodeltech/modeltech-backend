package com.modeltech.datamasteryhub.modules.networking.controller;

import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.ProjectMemberRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectScreenshotResponse;
import com.modeltech.datamasteryhub.modules.networking.service.ProjectService;
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
@RequestMapping("/api/v1/admin/projects")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin - Projects", description = "Gestion des projets alumni")
public class AdminProjectController {

    private final ProjectService projectService;

    // ── CRUD Projet ───────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Créer un projet (avec membres initiaux)")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @GetMapping
    @Operation(summary = "Lister tous les projets (paginé)")
    public ResponseEntity<Page<ProjectResponse>> getAll(
            @PageableDefault(size = 10, sort = "displayOrder") Pageable pageable) {
        return ResponseEntity.ok(projectService.findAllForAdmin(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un projet par ID")
    public ResponseEntity<ProjectResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.findByIdForAdmin(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour les infos d'un projet")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un projet (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/members")
    @Operation(summary = "Ajouter un membre alumni au projet")
    public ResponseEntity<ProjectResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectMemberRequest request) {
        return ResponseEntity.ok(projectService.addMember(id, request));
    }

    @DeleteMapping("/{id}/members/{alumniId}")
    @Operation(summary = "Retirer un membre alumni du projet")
    public ResponseEntity<ProjectResponse> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID alumniId) {
        return ResponseEntity.ok(projectService.removeMember(id, alumniId));
    }

    // ── Cover image ───────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader l'image de couverture du projet (MinIO)")
    public ResponseEntity<ProjectResponse> uploadCover(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(projectService.uploadCoverImage(id, file));
    }

    // ── Screenshots ───────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/screenshots", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ajouter un screenshot au projet (MinIO)")
    public ResponseEntity<ProjectScreenshotResponse> addScreenshot(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            @RequestParam(defaultValue = "0") int displayOrder) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addScreenshot(id, file, caption, displayOrder));
    }

    @DeleteMapping("/{id}/screenshots/{screenshotId}")
    @Operation(summary = "Supprimer un screenshot du projet (+ suppression MinIO)")
    public ResponseEntity<Void> deleteScreenshot(
            @PathVariable UUID id,
            @PathVariable UUID screenshotId) {
        projectService.deleteScreenshot(id, screenshotId);
        return ResponseEntity.noContent().build();
    }
}
