package com.modeltech.datamasteryhub.modules.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Frais du prestataire de paiement : <b>absorbés</b> par l'entreprise (défaut, le client paie le prix du catalogue) ou <b>répercutés</b> sur le
 * client qui paie en ligne. Se règle par variables d'environnement, sans toucher au code :
 * <ul>
 *   <li>{@code PAYMENTS_FEES_MODE} = {@code ABSORB} (défaut) ou {@code PASS_ON} ;</li>
 *   <li>{@code PAYMENTS_FEES_PERCENT} : taux du prestataire en % (ex. 3.5) ;</li>
 *   <li>{@code PAYMENTS_FEES_FIXED_XOF} : part fixe éventuelle par transaction.</li>
 * </ul>
 * Répercuter les frais, c'est facturer le montant qui, <i>une fois les frais retirés</i>, laisse exactement le prix du catalogue :
 * {@code (prix + fixe) / (1 − taux)}, arrondi à l'unité supérieure. Seul le paiement <b>en ligne</b> porte des frais : le paiement manuel
 * reste au prix du catalogue.
 */
@Component
public class PaymentFees {

    public enum Mode { ABSORB, PASS_ON }

    private final Mode mode;
    private final BigDecimal percent;
    private final int fixedXof;

    public PaymentFees(@Value("${app.payments.fees.mode:ABSORB}") String mode,
                       @Value("${app.payments.fees.percent:0}") BigDecimal percent,
                       @Value("${app.payments.fees.fixed-xof:0}") int fixedXof) {
        try {
            this.mode = Mode.valueOf(mode == null ? "ABSORB" : mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PAYMENTS_FEES_MODE doit valoir ABSORB ou PASS_ON (reçu : « " + mode + " »).");
        }
        if (percent == null || percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(20)) >= 0) {
            throw new IllegalStateException("PAYMENTS_FEES_PERCENT doit être compris entre 0 et 20 (reçu : " + percent + ").");
        }
        if (fixedXof < 0 || fixedXof > 5000) {
            throw new IllegalStateException("PAYMENTS_FEES_FIXED_XOF doit être compris entre 0 et 5000 (reçu : " + fixedXof + ").");
        }
        this.percent = percent;
        this.fixedXof = fixedXof;
    }

    public Mode mode() {
        return mode;
    }

    public BigDecimal percent() {
        return percent;
    }

    public int fixedXof() {
        return fixedXof;
    }

    /** Montant à encaisser en ligne pour un prix de catalogue donné (frais compris si on les répercute). */
    public long onlineTotal(long baseXof) {
        if (mode == Mode.ABSORB || (percent.signum() == 0 && fixedXof == 0)) return baseXof;
        BigDecimal keep = BigDecimal.ONE.subtract(percent.movePointLeft(2));
        return BigDecimal.valueOf(baseXof + fixedXof).divide(keep, 0, RoundingMode.CEILING).longValueExact();
    }

    /** Part des frais incluse dans {@link #onlineTotal(long)} (0 si on les absorbe). */
    public long onlineFee(long baseXof) {
        return onlineTotal(baseXof) - baseXof;
    }
}
