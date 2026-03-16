package com.modeltech.datamasteryhub.modules.networking.controller;

import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniSummaryResponse;
import com.modeltech.datamasteryhub.modules.networking.service.AlumniService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alumni")
@RequiredArgsConstructor
@Tag(name = "Public - Alumni", description = "Endpoints publics — alumni")
public class PublicAlumniController {

    private final AlumniService alumniService;

    @GetMapping
    @Operation(summary = "Lister tous les alumni publiés (triés par display_order)")
    public ResponseEntity<List<AlumniSummaryResponse>> getAllPublished() {
        return ResponseEntity.ok(alumniService.findAllPublished());
    }
}
