package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.simulator.dto.SimulatorPayloads.*;
import com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.ItemType;
import com.modeltech.datamasteryhub.modules.simulator.service.QuestionBankService;
import com.modeltech.datamasteryhub.security.Actor;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Banque de questions du simulateur (administration). Les droits sont portés par la table des routes
 * ({@code AdminAccessRules}) ; l'identité de l'acteur vient de la session. Ces réponses contiennent les corrigés.
 */
@RestController
@RequestMapping("/api/v1/admin/simulator")
@RequiredArgsConstructor
@Tag(name = "Admin - Simulateur")
public class AdminSimulatorController {

    private final QuestionBankService bank;

    @GetMapping("/blueprint")
    public ResponseEntity<ApiResponse<BlueprintView>> blueprint() {
        return ResponseEntity.ok(ApiResponse.ok("Blueprint en vigueur", bank.blueprint()));
    }

    @GetMapping("/coverage")
    public ResponseEntity<ApiResponse<List<CoverageRow>>> coverage() {
        return ResponseEntity.ok(ApiResponse.ok("Couverture par compétence", bank.coverage()));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<ItemSummary>>> search(
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ItemSummary> result = bank.search(status, domain, skill, type, q, mine ? Actor.email() : null, page, size);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " question(s)", result));
    }

    @GetMapping("/items/{code}")
    public ResponseEntity<ApiResponse<ItemVersionView>> get(@PathVariable String code, @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(ApiResponse.ok("Question", bank.get(code, version)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<ItemVersionView>> create(@Valid @RequestBody ItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Brouillon créé", bank.create(request, Actor.email())));
    }

    @PostMapping("/items/import")
    public ResponseEntity<ApiResponse<ImportResult>> importItems(@Valid @RequestBody ImportRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Import terminé", bank.importItems(request, Actor.email())));
    }

    @PutMapping("/items/{code}/versions/{version}")
    public ResponseEntity<ApiResponse<ItemVersionView>> update(@PathVariable String code, @PathVariable int version,
                                                               @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Brouillon enregistré", bank.update(code, version, request, Actor.email())));
    }

    @PostMapping("/items/{code}/versions")
    public ResponseEntity<ApiResponse<ItemVersionView>> newVersion(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Nouvelle version ouverte", bank.newVersion(code, Actor.email())));
    }

    @PostMapping("/items/{code}/versions/{version}/submit")
    public ResponseEntity<ApiResponse<ItemVersionView>> submit(@PathVariable String code, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.ok("Soumise à relecture", bank.submit(code, version, Actor.email())));
    }

    @PostMapping("/items/{code}/versions/{version}/review")
    public ResponseEntity<ApiResponse<ItemVersionView>> review(@PathVariable String code, @PathVariable int version,
                                                               @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(request.approve() ? "Question approuvée" : "Corrections demandées",
                bank.review(code, version, request, Actor.email())));
    }

    @PostMapping("/items/{code}/versions/{version}/publish")
    public ResponseEntity<ApiResponse<ItemVersionView>> publish(@PathVariable String code, @PathVariable int version) {
        return ResponseEntity.ok(ApiResponse.ok("Question publiée", bank.publish(code, version, Actor.email())));
    }

    @PostMapping("/items/{code}/versions/{version}/retire")
    public ResponseEntity<ApiResponse<ItemVersionView>> retire(@PathVariable String code, @PathVariable int version,
                                                               @Valid @RequestBody RetireRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Question retirée", bank.retire(code, version, request.reason(), Actor.email())));
    }

    @DeleteMapping("/items/{code}/versions/{version}")
    public ResponseEntity<Void> deleteDraft(@PathVariable String code, @PathVariable int version) {
        bank.deleteDraft(code, version, Actor.email());
        return ResponseEntity.noContent().build();
    }
}
