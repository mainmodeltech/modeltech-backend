package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.LearnerBillingPayloads;
import com.modeltech.datamasteryhub.modules.training.service.LearnerBillingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Paiements, reçus et attestations de l'apprenant (rôle LEARNER, JSON brut et PDF). */
@RestController
@RequestMapping("/api/v1/learner")
@RequiredArgsConstructor
@Tag(name = "Apprenant - Paiements et documents")
public class LearnerBillingController {

    private final LearnerBillingService billingService;

    @GetMapping("/payments")
    public List<LearnerBillingPayloads.PaymentItem> payments(Authentication authentication) {
        return billingService.payments(authentication.getName());
    }

    @GetMapping("/payments/{paymentId}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID paymentId, Authentication authentication) {
        return pdf(billingService.receipt(authentication.getName(), paymentId), "Recu-de-paiement.pdf");
    }

    @GetMapping("/enrollments")
    public List<LearnerBillingPayloads.EnrollmentItem> enrollments(Authentication authentication) {
        return billingService.enrollments(authentication.getName());
    }

    @GetMapping("/enrollments/{formationId}/attestation")
    public ResponseEntity<byte[]> attestation(@PathVariable UUID formationId, Authentication authentication) {
        return pdf(billingService.attestation(authentication.getName(), formationId), "Attestation-d-inscription.pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
