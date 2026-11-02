package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.simulator.dto.GrantPayloads.*;
import com.modeltech.datamasteryhub.modules.simulator.service.AccessService;
import com.modeltech.datamasteryhub.security.Actor;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Droits d'usage du simulateur (droit {@code SIMULATOR_MANAGE} : ils contiennent des données personnelles). */
@RestController
@RequestMapping("/api/v1/admin/simulator/grants")
@RequiredArgsConstructor
@Tag(name = "Admin - Simulateur")
public class AdminSimulatorGrantController {

    private final AccessService access;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GrantView>>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<GrantView> result = access.list(page, size);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " droit(s) d'usage", result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GrantView>> grant(@Valid @RequestBody GrantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Accès accordé", access.grant(request, Actor.email())));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<GrantView>> revoke(@PathVariable UUID id, @Valid @RequestBody RevokeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Accès révoqué", access.revoke(id, request.reason(), Actor.email())));
    }
}
