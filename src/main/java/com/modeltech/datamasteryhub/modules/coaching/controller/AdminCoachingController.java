package com.modeltech.datamasteryhub.modules.coaching.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.ProofLink;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.ReasonRequest;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.ReviewUpdate;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.Summary;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.ValidateRequest;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingStatus;
import com.modeltech.datamasteryhub.modules.coaching.service.CoachingPaymentService;
import com.modeltech.datamasteryhub.modules.coaching.service.CoachingService;
import com.modeltech.datamasteryhub.security.Actor;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Demandes de coaching (administration). Les droits sont posés par {@code AdminAccessRules} : traiter, valider et annuler = COACHING_MANAGE
 * (responsable pédagogique) ; lire = COACHING_MANAGE ou PAYMENTS_VIEW ; confirmer ou refuser un paiement manuel = PAYMENTS_MANAGE (Finance).
 */
@RestController
@RequestMapping("/api/v1/admin/coaching-requests")
@RequiredArgsConstructor
@Tag(name = "Admin - Coaching")
public class AdminCoachingController {

    private final CoachingService coaching;
    private final CoachingPaymentService payments;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RequestView>>> list(@RequestParam(required = false) CoachingStatus status,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        var result = coaching.list(status, page, size);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " demande(s)", result));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Summary>> summary() {
        return ResponseEntity.ok(ApiResponse.ok("Demandes par statut", coaching.summary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestView>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Demande de coaching", coaching.get(id)));
    }

    @PostMapping("/{id}/take")
    public ResponseEntity<ApiResponse<RequestView>> take(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Demande prise en charge", coaching.take(id, Actor.email())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestView>> update(@PathVariable UUID id, @Valid @RequestBody ReviewUpdate body) {
        return ResponseEntity.ok(ApiResponse.ok("Demande mise à jour", coaching.update(id, body, Actor.email())));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<RequestView>> validate(@PathVariable UUID id, @Valid @RequestBody ValidateRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Coaching validé : le lien de paiement est envoyé", coaching.validate(id, body, Actor.email())));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<ApiResponse<RequestView>> decline(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Demande refusée", coaching.decline(id, body.reason(), Actor.email())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestView>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Coaching annulé", coaching.cancel(id, body.reason(), Actor.email())));
    }

    @PostMapping("/{id}/resend-link")
    public ResponseEntity<ApiResponse<RequestView>> resend(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Lien de paiement renvoyé", coaching.resendLink(id)));
    }

    @PostMapping("/{id}/payment/confirm")
    public ResponseEntity<ApiResponse<RequestView>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Paiement confirmé", payments.confirm(id, Actor.email())));
    }

    @PostMapping("/{id}/payment/reject")
    public ResponseEntity<ApiResponse<RequestView>> reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Paiement refusé", payments.reject(id, body.reason(), Actor.email())));
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<ApiResponse<ProofLink>> proof(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Justificatif", new ProofLink(payments.proofLink(id))));
    }
}
