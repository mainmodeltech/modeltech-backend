package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreatePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.SessionFinancialSummaryResponse;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Paiements")
public class AdminPaymentController {

    private final PaymentService paymentService;

    // ── Paiements par inscription ────────────────────────────────────────────

    @PostMapping("/registrations/{registrationId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> addPayment(
            @PathVariable UUID registrationId,
            @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Paiement enregistre", paymentService.addPayment(registrationId, request)));
    }

    @GetMapping("/registrations/{registrationId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPayments(
            @PathVariable UUID registrationId) {
        List<PaymentResponse> payments = paymentService.findByRegistration(registrationId);
        return ResponseEntity.ok(ApiResponse.ok(payments.size() + " paiement(s)", payments));
    }

    @DeleteMapping("/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable UUID paymentId) {
        paymentService.deletePayment(paymentId);
    }

    // ── Resume financier par session ─────────────────────────────────────────

    @GetMapping("/sessions/{sessionId}/financial-summary")
    public ResponseEntity<ApiResponse<SessionFinancialSummaryResponse>> getFinancialSummary(
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok("Resume financier",
                paymentService.getFinancialSummary(sessionId)));
    }
}
