package com.modeltech.datamasteryhub.modules.payments;

import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayException;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Règle d'or du paiement en ligne, commune à tous les services : <b>rien n'est cru sur la foi du navigateur ni d'une notification</b>.
 * L'état d'un paiement est relu auprès du prestataire (jeton → statut, montant, référence, mode) et comparé à ce qu'on attendait.
 * Le service appelant décide ensuite quoi faire du verdict (ouvrir un accès, confirmer des échéances…), sous verrou de ligne.
 */
@Component
@Slf4j
public class OnlineCheckoutVerifier {

    public enum Outcome {
        /** Payé, et cohérent avec ce qui était attendu : on peut ouvrir. */
        PAID,
        /** Pas encore payé (ou inconnu du prestataire) : on laisse ouvert. */
        PENDING,
        CANCELLED,
        FAILED,
        /** Le prestataire dit « payé » mais montant, référence ou mode ne correspondent pas : rien n'est ouvert, contrôle humain. */
        MISMATCH,
        /** Prestataire injoignable : on réessaiera plus tard. */
        UNREACHABLE
    }

    /** @param detail motif lisible (échec, incohérence) ; {@code null} sinon. @param payment réponse du prestataire, {@code null} si injoignable. */
    public record Verdict(Outcome outcome, String detail, GatewayPayment payment) {}

    private final PaymentGateway gateway;

    public OnlineCheckoutVerifier(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public Verdict verify(String providerToken, long expectedChargedXof, String expectedRef) {
        GatewayPayment p;
        try {
            p = gateway.fetchStatus(providerToken);
        } catch (GatewayException e) {
            log.warn("Vérification d'un paiement impossible pour l'instant : {}", e.getMessage());
            return new Verdict(Outcome.UNREACHABLE, e.getMessage(), null);
        }
        return switch (p.state()) {
            case COMPLETED -> {
                List<String> problems = new ArrayList<>();
                if (p.totalAmount() != expectedChargedXof) problems.add("montant reçu " + p.totalAmount() + " au lieu de " + expectedChargedXof);
                if (expectedRef == null || !expectedRef.equals(p.orderRef())) problems.add("référence de commande différente");
                if (!modeMatches(p.mode())) problems.add("mode « " + p.mode() + " » inattendu (attendu : " + gateway.mode() + ")");
                yield problems.isEmpty()
                        ? new Verdict(Outcome.PAID, null, p)
                        : new Verdict(Outcome.MISMATCH, String.join(" ; ", problems), p);
            }
            case CANCELLED -> new Verdict(Outcome.CANCELLED, "Paiement annulé", p);
            case FAILED -> new Verdict(Outcome.FAILED, p.failReason() != null ? p.failReason() : "Paiement refusé", p);
            default -> new Verdict(Outcome.PENDING, null, p);
        };
    }

    private boolean modeMatches(String providerMode) {
        if (providerMode == null || providerMode.isBlank()) return true;   // non communiqué : le jeton (préfixe « test_ ») et la clé suffisent
        String m = providerMode.trim().toLowerCase(Locale.ROOT);
        return gateway.mode().equals("live") ? m.equals("live") : (m.equals("test") || m.equals("sandbox"));
    }
}
