package com.modeltech.datamasteryhub.modules.payments;

/**
 * Moyens de paiement offerts pour un service. Le manuel (Wave, Orange Money, virement déclarés puis confirmés par l'équipe) reste
 * toujours possible en secours : même en « en ligne seulement », il est proposé automatiquement si la passerelle est indisponible.
 */
public enum PaymentMode {
    MANUAL_ONLY("Manuel seulement", false, true),
    ONLINE_AND_MANUAL("En ligne et manuel", true, true),
    ONLINE_ONLY("En ligne (manuel en secours)", true, false);

    private final String label;
    private final boolean online;
    private final boolean manualAlways;

    PaymentMode(String label, boolean online, boolean manualAlways) {
        this.label = label;
        this.online = online;
        this.manualAlways = manualAlways;
    }

    public String label() {
        return label;
    }

    /** Vrai si le paiement en ligne est voulu pour ce service. */
    public boolean wantsOnline() {
        return online;
    }

    /** Vrai si le paiement manuel est toujours proposé (et pas seulement en secours). */
    public boolean manualAlways() {
        return manualAlways || this == ONLINE_AND_MANUAL;
    }
}
