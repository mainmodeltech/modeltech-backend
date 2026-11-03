package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.training.dto.request.DeclarePaymentRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.CheckoutView;
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.StartRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.OnlineCheckoutPayloads.StartResponse;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationCheckoutService;
import com.modeltech.datamasteryhub.modules.training.dto.response.PublicPaymentResponse;
import com.modeltech.datamasteryhub.modules.training.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Page « lien de paiement » : le jeton du lien est le seul secret (256 bits, expirant). */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Public - Paiements")
public class PublicPaymentController {

    private final PaymentService paymentService;
    private final RegistrationCheckoutService checkoutService;
    private final IpRateLimiter rateLimiter;
    private final com.modeltech.datamasteryhub.modules.training.service.InvoiceService invoiceService;
    private final com.modeltech.datamasteryhub.modules.training.service.LearnerBillingService billingService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PublicPaymentResponse>> get(@PathVariable String token, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, IpRateLimiter.TOKEN_VIEW_SCOPE);   // freine le balayage de jetons
        PublicPaymentResponse response = paymentService.getByToken(token);
        response.setOnline(checkoutService.optionsFor(token));
        return ResponseEntity.ok(ApiResponse.ok("Paiement", response));
    }

    /** Démarre le paiement en ligne d'une échéance ou de tout le solde : renvoie l'adresse de la page du prestataire. */
    @PostMapping("/{token}/online")
    public ResponseEntity<ApiResponse<StartResponse>> startOnline(
            @PathVariable String token,
            @Valid @RequestBody StartRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "payment-online");
        return ResponseEntity.ok(ApiResponse.ok("Paiement en ligne", checkoutService.start(token, request.scope())));
    }

    /** Suivi d'une tentative au retour du prestataire (référence opaque) ; relit le prestataire si elle est encore en attente. */
    @GetMapping("/online-checkouts/{ref}")
    public ResponseEntity<ApiResponse<CheckoutView>> checkoutStatus(@PathVariable java.util.UUID ref, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, IpRateLimiter.TOKEN_VIEW_SCOPE);
        return ResponseEntity.ok(ApiResponse.ok("Paiement en ligne", checkoutService.status(ref)));
    }

    /** Facture de l'inscription (entreprises), téléchargeable depuis le lien de paiement. */
    @GetMapping("/{token}/invoice")
    public ResponseEntity<byte[]> invoice(@PathVariable String token, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "payment-invoice");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"Facture.pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(invoiceService.pdfForPaymentToken(token));
    }

    /** Reçu d'une échéance confirmée, téléchargeable depuis le lien de paiement (candidat sans compte). */
    @GetMapping("/{token}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable String token, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "payment-invoice");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"Recu-de-paiement.pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(billingService.receiptByToken(token));
    }

    @PostMapping("/{token}/declaration")
    public ResponseEntity<ApiResponse<PublicPaymentResponse>> declare(
            @PathVariable String token,
            @Valid @RequestBody DeclarePaymentRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "payment-declaration");
        return ResponseEntity.ok(ApiResponse.ok("Paiement déclaré, nous le vérifions rapidement",
                paymentService.declare(token, request)));
    }

    @PostMapping(value = "/{token}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PublicPaymentResponse>> uploadProof(
            @PathVariable String token,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "payment-proof");
        return ResponseEntity.ok(ApiResponse.ok("Justificatif enregistré", paymentService.uploadProof(token, file)));
    }
}
