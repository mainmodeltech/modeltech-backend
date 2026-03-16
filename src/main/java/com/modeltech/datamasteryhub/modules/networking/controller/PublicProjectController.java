package com.modeltech.datamasteryhub.modules.networking.controller;

import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectSummaryResponse;
import com.modeltech.datamasteryhub.modules.networking.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Public - Projects", description = "Endpoints publics — projets alumni")
public class PublicProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "Lister tous les projets publiés")
    public ResponseEntity<List<ProjectSummaryResponse>> getAllPublished() {
        return ResponseEntity.ok(projectService.findAllPublished());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un projet publié par ID")
    public ResponseEntity<ProjectResponse> getPublishedById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.findPublishedById(id));
    }
}

