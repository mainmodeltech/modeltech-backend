package com.modeltech.datamasteryhub.modules.training.dto.response;

import com.modeltech.datamasteryhub.modules.training.enums.CheckoutScope;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Paiement en ligne d'une inscription (échéance ou solde). Montants en francs CFA (XOF, sans décimale). */
public final class OnlineCheckoutPayloads {

    private OnlineCheckoutPayloads() {}

    public record StartRequest(@NotNull CheckoutScope scope) {}

    /** Adresse de la page de paiement du prestataire, et référence opaque pour suivre la tentative au retour. */
    public record StartResponse(UUID checkoutRef, String checkoutUrl) {}

    /** Ce que voit le payeur au retour du prestataire : jamais de jeton ni de détail d'un échec. */
    public record CheckoutView(UUID ref, CheckoutStatus status, CheckoutScope scope, long amountXof, long feeXof, long chargedXof, int installments, String receiptUrl) {}

    /** @param amount prix du catalogue ; @param fee frais répercutés (0 si absorbés) ; @param total montant encaissé en ligne ; @param count échéances couvertes */
    public record Amounts(long amount, long fee, long total, int count) {}

    /**
     * Moyens de paiement offerts pour ce lien. {@code installment} : cette échéance seule ; {@code balance} : tout le solde restant,
     * présent seulement s'il reste au moins deux échéances à régler.
     */
    public record OnlineOptions(boolean online, boolean manual, boolean fallback, String reason, Amounts installment, Amounts balance) {}
}
