package com.modeltech.datamasteryhub.modules.coaching.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.CheckoutView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.PayView;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestForm;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.RequestReceipt;
import com.modeltech.datamasteryhub.modules.coaching.dto.CoachingPayloads.StartResponse;
import com.modeltech.datamasteryhub.modules.coaching.service.CoachingPaymentService;
import com.modeltech.datamasteryhub.modules.coaching.service.CoachingService;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.DeclareRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Coaching, côté public : la demande, puis la page de paiement à lien secret (le jeton de 256 bits est le seul secret). Rien d'interne
 * (notes, e-mail, téléphone) n'est jamais renvoyé ici.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public - Coaching")
public class PublicCoachingController {

    private final CoachingService coaching;
    private final CoachingPaymentService payments;
    private final IpRateLimiter rateLimiter;

    @PostMapping("/api/v1/coaching-requests")
    public ResponseEntity<ApiResponse<RequestReceipt>> request(@Valid @RequestBody RequestForm form, HttpServletRequest http) {
        rateLimiter.check(http, "coaching-request");
        RequestReceipt receipt = coaching.submit(form);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(receipt.message(), receipt));
    }

    @GetMapping("/api/v1/coaching/payments/{token}")
    public ResponseEntity<ApiResponse<PayView>> view(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.check(http, IpRateLimiter.TOKEN_VIEW_SCOPE);   // freine le balayage de jetons
        return ResponseEntity.ok(ApiResponse.ok("Coaching", payments.view(token)));
    }

    @PostMapping("/api/v1/coaching/payments/{token}/online")
    public ResponseEntity<ApiResponse<StartResponse>> startOnline(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.check(http, "coaching-payment-online");
        return ResponseEntity.ok(ApiResponse.ok("Paiement en ligne", payments.startOnline(token)));
    }

    @PostMapping("/api/v1/coaching/payments/{token}/declaration")
    public ResponseEntity<ApiResponse<PayView>> declare(@PathVariable String token, @Valid @RequestBody DeclareRequest body, HttpServletRequest http) {
        rateLimiter.check(http, "coaching-payment-declaration");
        return ResponseEntity.ok(ApiResponse.ok("Paiement déclaré, nous le vérifions rapidement", payments.declare(token, body)));
    }

    @PostMapping(value = "/api/v1/coaching/payments/{token}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PayView>> proof(@PathVariable String token, @RequestPart("file") MultipartFile file, HttpServletRequest http) {
        rateLimiter.check(http, "coaching-payment-proof");
        return ResponseEntity.ok(ApiResponse.ok("Justificatif enregistré", payments.uploadProof(token, file)));
    }

    /** Suivi d'une tentative au retour du prestataire (référence opaque) ; relit le prestataire si elle est encore en attente. */
    @GetMapping("/api/v1/coaching/checkouts/{ref}")
    public ResponseEntity<ApiResponse<CheckoutView>> checkout(@PathVariable UUID ref, HttpServletRequest http) {
        rateLimiter.check(http, IpRateLimiter.TOKEN_VIEW_SCOPE);
        return ResponseEntity.ok(ApiResponse.ok("Paiement en ligne", payments.status(ref)));
    }
}
