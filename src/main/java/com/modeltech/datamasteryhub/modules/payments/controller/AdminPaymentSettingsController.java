package com.modeltech.datamasteryhub.modules.payments.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.payments.PaymentSettingsService;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.PaymentSettingsUpdate;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.PaymentSettingsView;
import com.modeltech.datamasteryhub.security.Actor;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Réglages de paiement : réservés à la Direction (voir {@code AdminAccessRules}). */
@RestController
@RequestMapping("/api/v1/admin/payment-settings")
@RequiredArgsConstructor
@Tag(name = "Paiements (Admin)")
public class AdminPaymentSettingsController {

    private final PaymentSettingsService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PaymentSettingsView>> get() {
        return ResponseEntity.ok(ApiResponse.ok("Réglages de paiement", service.current()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PaymentSettingsView>> update(@Valid @RequestBody PaymentSettingsUpdate request) {
        return ResponseEntity.ok(ApiResponse.ok("Réglages de paiement enregistrés", service.update(request, Actor.email())));
    }

    @PostMapping("/gateway/reset")
    public ResponseEntity<ApiResponse<PaymentSettingsView>> resetGateway() {
        return ResponseEntity.ok(ApiResponse.ok("Alerte remise à zéro", service.resetGateway(Actor.email())));
    }
}
