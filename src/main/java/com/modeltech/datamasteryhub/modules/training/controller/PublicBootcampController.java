package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSessionResponse;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bootcamps")
@RequiredArgsConstructor
@Tag(name = "Bootcamps (Public)", description = "Endpoints publics pour consulter les bootcamps")
public class PublicBootcampController {

    private final BootcampService bootcampService;

    @GetMapping
    @Operation(summary = "Liste tous les bootcamps publiés avec leur prochaine session")
    public ResponseEntity<List<BootcampResponse>> findAll() {
        return ResponseEntity.ok(bootcampService.findAllPublished());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un bootcamp avec toutes ses sessions")
    public ResponseEntity<BootcampResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(bootcampService.findPublishedById(id));
    }

    @GetMapping("/{id}/sessions")
    @Operation(summary = "Sessions d'un bootcamp (publiques uniquement)")
    public ResponseEntity<List<BootcampSessionResponse>> findSessions(@PathVariable UUID id) {
        return ResponseEntity.ok(bootcampService.findSessionsByBootcamp(id, false));
    }
}
