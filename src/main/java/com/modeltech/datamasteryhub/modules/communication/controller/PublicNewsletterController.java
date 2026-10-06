package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.communication.dto.request.NewsletterSubscribeRequest;
import com.modeltech.datamasteryhub.modules.communication.dto.request.NewsletterTokenRequest;
import com.modeltech.datamasteryhub.modules.communication.service.NewsletterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/newsletter/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Newsletter (Public)", description = "Abonnement avec double opt-in")
public class PublicNewsletterController {

    private final NewsletterService newsletterService;
    private final IpRateLimiter rateLimiter;

    @PostMapping
    @Operation(summary = "S'abonner à la newsletter (envoie un e-mail de confirmation)",
            description = "Réponse identique que l'adresse soit déjà connue ou non. Limité par IP.")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            @Valid @RequestBody NewsletterSubscribeRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "newsletter");
        newsletterService.subscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
                "Si cette adresse est valide, un e-mail de confirmation vient de vous être envoyé.", null));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirmer l'abonnement avec le jeton reçu par e-mail")
    public ResponseEntity<ApiResponse<Void>> confirm(
            @Valid @RequestBody NewsletterTokenRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "newsletter");
        newsletterService.confirm(request.getToken());
        return ResponseEntity.ok(ApiResponse.ok("Votre inscription à la newsletter est confirmée. Merci !", null));
    }

    @PostMapping("/unsubscribe")
    @Operation(summary = "Se désinscrire avec le jeton de désinscription")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            @Valid @RequestBody NewsletterTokenRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "newsletter");
        newsletterService.unsubscribe(request.getToken());
        return ResponseEntity.ok(ApiResponse.ok("Vous êtes désinscrit(e) de la newsletter.", null));
    }
}
