package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.training.dto.request.AcceptRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.ManualPaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.ManualRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.RejectReasonRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateRegistrationStatusRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/registrations")
@RequiredArgsConstructor
public class AdminRegistrationController {

    private final RegistrationService registrationService;
    private final PaymentService paymentService;

    @GetMapping
    public Page<RegistrationResponse> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        return registrationService.findAllForAdmin(pageable, status);
    }

    @GetMapping("/{id}")
    public RegistrationResponse getById(@PathVariable UUID id) {
        return registrationService.findByIdForAdmin(id);
    }

    @PatchMapping("/{id}/status")
    public RegistrationResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRegistrationStatusRequest request
    ) {
        return registrationService.updateStatus(id, request.getStatus());
    }

    /** « Inscription manuelle » : candidature saisie par l'équipe, ensuite acceptée comme les autres. */
    @PostMapping
    public ResponseEntity<ApiResponse<RegistrationResponse>> create(
            @Valid @RequestBody ManualRegistrationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Candidature enregistrée",
                registrationService.createManually(request, authentication.getName())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RegistrationResponse>> cancel(
            @PathVariable UUID id, @Valid @RequestBody RejectReasonRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Inscription annulée",
                paymentService.cancelRegistration(id, request.getReason(), authentication.getName())));
    }

    // ── Parcours candidature → paiement (réponses ApiResponse : endpoints récents) ──

    /** Accepte la candidature : calcule le montant, crée les échéances, envoie le lien de paiement. */
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<RegistrationResponse>> accept(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AcceptRegistrationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Candidature acceptée, lien de paiement envoyé",
                paymentService.accept(id, request, authentication.getName())));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RegistrationResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Candidature refusée",
                paymentService.rejectRegistration(id, request.getReason())));
    }

    /** Enregistre un paiement reçu hors du site (virement, espèces, facture entreprise). */
    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody ManualPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Paiement enregistré, à confirmer",
                paymentService.recordManualPayment(id, request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        registrationService.softDelete(id);
    }
}
