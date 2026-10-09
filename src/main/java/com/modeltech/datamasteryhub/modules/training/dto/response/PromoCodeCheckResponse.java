package com.modeltech.datamasteryhub.modules.training.dto.response;

/**
 * Réponse PUBLIQUE de la validation d'un code promo : uniquement ce dont le formulaire a besoin.
 * Ne jamais y ajouter d'information sur le parrain (nom, e-mail, téléphone) ni sur l'usage du code.
 */
public record PromoCodeCheckResponse(boolean valid, String code, Integer discountPercent, String message) {

    public static PromoCodeCheckResponse of(PromoCodeResponse promo) {
        return new PromoCodeCheckResponse(true, promo.getCode(), promo.getDiscountPercent(), "Code valide");
    }
}
