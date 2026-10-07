package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Montant dû pour une inscription. Règles (validées) :
 * <ol>
 *   <li>prix de base = surcharge de la session, sinon prix de la formation ;</li>
 *   <li>si la session a un tarif early-bird et que la candidature a été déposée au plus tard à la
 *       date limite, le tarif early-bird remplace le prix de base ;</li>
 *   <li>le pourcentage du code promo (figé sur l'inscription) s'applique ensuite sur ce prix.</li>
 * </ol>
 * Aucun montant n'est déduit des prix « texte » d'affichage : sans prix numérique, il n'y a pas de devis.
 */
@Component
public class RegistrationPricing {

    public record Quote(long baseAmount, boolean earlyBird, int discountPercent, long discountAmount, long total) {}

    public Optional<Quote> quote(Registration registration) {
        BootcampSession session = registration.getSession();
        Bootcamp bootcamp = registration.getBootcamp() != null
                ? registration.getBootcamp()
                : (session != null ? session.getBootcamp() : null);

        Long base = null;
        if (session != null && session.getPriceOverrideAmount() != null) {
            base = session.getPriceOverrideAmount();
        } else if (bootcamp != null) {
            base = bootcamp.getPriceAmount();
        }

        boolean earlyBird = false;
        if (session != null && session.getEarlyBirdAmount() != null && session.getEarlyBirdDeadline() != null) {
            LocalDate appliedOn = registration.getCreatedAt() != null
                    ? registration.getCreatedAt().toLocalDate() : LocalDate.now();
            if (!appliedOn.isAfter(session.getEarlyBirdDeadline())) {
                base = session.getEarlyBirdAmount();
                earlyBird = true;
            }
        }
        if (base == null) return Optional.empty();

        int percent = registration.getDiscountPercent() == null
                ? 0 : Math.max(0, Math.min(100, registration.getDiscountPercent()));
        long discount = BigDecimal.valueOf(base)
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return Optional.of(new Quote(base, earlyBird, percent, discount, base - discount));
    }
}
