package com.modeltech.datamasteryhub.modules.training.controller;

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
     * Valider un code promo cote visiteur.
     * Retourne le discountPercent si valide, 404 sinon.
     */
    @GetMapping("/validate")
    public ResponseEntity<PromoCodeResponse> validate(@RequestParam String code) {
        PromoCodeResponse result = promoCodeService.validateCode(code);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
