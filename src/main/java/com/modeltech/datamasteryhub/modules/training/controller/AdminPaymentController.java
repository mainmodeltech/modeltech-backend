package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.training.dto.request.RejectReasonRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.AdminPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.EnrollmentResponse;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Paiements des inscriptions et accès apprenants (rôles ADMIN / SUPER_ADMIN, cf. SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Paiements")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<List<AdminPaymentResponse>>> getAll(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID registrationId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<AdminPaymentResponse> page = paymentService.findAllForAdmin(status, registrationId, pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " paiement(s)", page));
    }

    @PostMapping("/payments/{id}/confirm")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> confirm(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Paiement confirmé", paymentService.confirm(id, authentication.getName())));
    }

    @PostMapping("/payments/{id}/reject")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody RejectReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Paiement refusé, le candidat est prévenu",
                paymentService.rejectPayment(id, request.getReason())));
    }

    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> refund(
            @PathVariable UUID id, @Valid @RequestBody RejectReasonRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Remboursement consigné",
                paymentService.refund(id, request.getReason(), authentication.getName())));
    }

    @PostMapping("/payments/{id}/remind")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> remind(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Relance envoyée", paymentService.remind(id)));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getEnrollments(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<EnrollmentResponse> page = paymentService.findEnrollments(pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " accès", page));
    }
}
