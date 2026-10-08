package com.modeltech.datamasteryhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mentions légales et réglages des factures ({@code app.invoice.*}). Aucune valeur d'identification légale
 * (NINEA, RCCM, banque…) n'est inventée : renseignées par variables d'environnement, elles apparaissent sur la facture.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.invoice")
public class InvoiceProperties {

    /** Préfixe du numéro : {@code FAC-2026-00001}. */
    private String numberPrefix = "FAC";

    /** TVA incluse dans les montants (0 = non applicable). Le total facturé est toujours le montant à payer. */
    private java.math.BigDecimal vatPercent = java.math.BigDecimal.ZERO;

    /** Délai de règlement par défaut, en jours, quand aucune échéance n'est définie. */
    private int defaultDueDays = 30;

    private Seller seller = new Seller();

    @Data
    public static class Seller {
        private String name = "Model Technologie";
        private String address = "";
        private String phone = "";
        private String email = "";
        private String website = "";
        /** NINEA (identifiant fiscal). */
        private String taxId = "";
        /** RCCM (registre du commerce). */
        private String registerNumber = "";
        /** Coordonnées bancaires / mobile money affichées en pied de facture. */
        private String paymentDetails = "";
        /** Mention libre en bas de page (pénalités de retard, TVA non applicable…). */
        private String footer = "";
    }
}
