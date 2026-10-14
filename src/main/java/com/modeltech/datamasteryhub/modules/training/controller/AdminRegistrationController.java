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
import com.modeltech.datamasteryhub.modules.training.service.RegistrationFilter;
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

    /**
     * Liste filtrée côté serveur : {@code stage} (NEW, AWAITING_PAYMENT, TO_CONFIRM, ENROLLED, ARCHIVED), {@code q} (nom, e-mail,
     * téléphone, entreprise, formation, code), {@code bootcampId}, {@code sessionId}. Tri : createdAt, lastName, status, bootcampTitle.
     */
    @GetMapping("/search")
    public Page<RegistrationResponse> search(
            @PageableDefault(size = 25, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) RegistrationFilter.Stage stage,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID bootcampId,
            @RequestParam(required = false) UUID sessionId) {
        return registrationService.search(sortable(pageable), new RegistrationFilter(stage, null, q, bootcampId, sessionId));
    }

    /** Nombre de candidatures par étape (onglets), pour la même recherche. */
    @GetMapping("/summary")
    public java.util.Map<String, Long> summary(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID bootcampId,
            @RequestParam(required = false) UUID sessionId) {
        return registrationService.stageCounts(new RegistrationFilter(null, null, q, bootcampId, sessionId));
    }

    /** Seuls quelques champs sont triables (un nom de champ inconnu ne doit pas provoquer d'erreur serveur) ; taille bornée. */
    private static Pageable sortable(Pageable pageable) {
        java.util.Set<String> allowed = java.util.Set.of("createdAt", "lastName", "firstName", "status", "bootcampTitle", "email");
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                pageable.getSort().stream().filter(o -> allowed.contains(o.getProperty())).toList());
        if (sort.isEmpty()) sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        return org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 200), sort);
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
