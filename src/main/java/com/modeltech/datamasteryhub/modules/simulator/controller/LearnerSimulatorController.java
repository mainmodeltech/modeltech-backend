package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.simulator.dto.LearnerSimulatorPayloads.*;
import com.modeltech.datamasteryhub.modules.simulator.service.AttemptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Simulateur côté apprenant (réservé au rôle LEARNER par la configuration de sécurité). L'identité vient de la session :
 * une tentative n'est jamais accessible par un autre apprenant. Aucune réponse de cette API ne contient de corrigé
 * pendant une tentative en cours (voir {@code LearnerSimulatorPayloads}).
 */
@RestController
@RequestMapping("/api/v1/learner/simulator")
@RequiredArgsConstructor
@Tag(name = "Apprenant - Simulateur")
public class LearnerSimulatorController {

    private final AttemptService attempts;

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<CatalogView>> catalog(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Simulateur", attempts.catalog(auth.getName())));
    }

    @GetMapping("/attempts")
    public ResponseEntity<ApiResponse<List<AttemptSummary>>> history(Authentication auth, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Historique", attempts.history(auth.getName(), limit)));
    }

    @PostMapping("/attempts")
    public ResponseEntity<ApiResponse<AttemptView>> start(Authentication auth, @Valid @RequestBody StartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tentative démarrée", attempts.start(auth.getName(), request)));
    }

    @GetMapping("/attempts/{id}")
    public ResponseEntity<ApiResponse<AttemptView>> get(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Tentative", attempts.get(auth.getName(), id)));
    }

    @PutMapping("/attempts/{id}/answers/{ref}")
    public ResponseEntity<ApiResponse<AnswerResult>> answer(Authentication auth, @PathVariable UUID id, @PathVariable UUID ref,
                                                            @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Réponse enregistrée", attempts.saveAnswer(auth.getName(), id, ref, request)));
    }

    @PostMapping("/attempts/{id}/submit")
    public ResponseEntity<ApiResponse<ReportView>> submit(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Tentative remise", attempts.submit(auth.getName(), id)));
    }

    @GetMapping("/attempts/{id}/report")
    public ResponseEntity<ApiResponse<ReportView>> report(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Rapport", attempts.report(auth.getName(), id)));
    }
}
