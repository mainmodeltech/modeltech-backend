package com.modeltech.datamasteryhub.modules.payments;

/** Les services qui encaissent de l'argent : chacun a ses propres réglages de paiement. */
public enum PayableService {
    /** Forfaits du simulateur de certification. */
    SIMULATOR("Simulateur de certification"),
    /** Inscriptions aux formations et bootcamps (échéances). */
    REGISTRATION("Inscriptions aux formations"),
    /** Coaching individuel (lien de paiement envoyé après l'appel de découverte). */
    COACHING("Coaching");

    private final String label;

    PayableService(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
