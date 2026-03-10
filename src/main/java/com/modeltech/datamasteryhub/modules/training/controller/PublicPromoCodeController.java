package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.response.PromoCodeResponse;
import com.modeltech.datamasteryhub.modules.training.service.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/promo-codes")
@RequiredArgsConstructor
public class PublicPromoCodeController {

    private final PromoCodeService promoCodeService;

    /**
     * Valider un code promo côté visiteur.
     * Retourne le discountPercent si valide, ou une ResourceNotFoundException avec message approprié.
     */
    @GetMapping("/validate")
    public ResponseEntity<PromoCodeResponse> validate(@RequestParam String code) {
        PromoCodeResponse result = promoCodeService.validateCode(code);

        // Si le service retourne null, on lance une exception avec le message approprié
        if (result == null) {
            throw new ResourceNotFoundException("Code promo invalide ou expiré : " + code);
        }

        return ResponseEntity.ok(result);
    }
}
