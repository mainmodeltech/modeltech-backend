package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bootcamps")
@RequiredArgsConstructor
@Tag(name = "Public - Bootcamps", description = "Endpoints publics en lecture seule pour les bootcamps")
public class PublicBootcampController {

    private final BootcampService bootcampService;

    @GetMapping
    @Operation(summary = "Lister les bootcamps publiés")
    public ResponseEntity<Page<BootcampSummaryResponse>> getAllPublished(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(bootcampService.findAllPublished(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un bootcamp publié par ID")
    @ApiResponse(responseCode = "200", description = "Bootcamp trouvé")
    @ApiResponse(responseCode = "404", description = "Bootcamp introuvable ou non publié")
    public ResponseEntity<BootcampResponse> getPublishedById(
            @Parameter(description = "UUID du bootcamp") @PathVariable UUID id) {
        return ResponseEntity.ok(bootcampService.findPublishedById(id));
    }
}
