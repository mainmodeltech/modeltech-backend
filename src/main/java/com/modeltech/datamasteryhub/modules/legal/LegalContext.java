package com.modeltech.datamasteryhub.modules.legal;

import java.util.List;

import static com.modeltech.datamasteryhub.modules.legal.LegalCode.CGU;
import static com.modeltech.datamasteryhub.modules.legal.LegalCode.CGV;
import static com.modeltech.datamasteryhub.modules.legal.LegalCode.PRIVACY;

/**
 * Les moments où une personne doit accepter des textes avant d'aller plus loin, et lesquels. Seuls les textes <b>publiés</b> sont exigés : tant que
 * la Direction n'a rien publié (après validation par le conseil juridique), rien ne bloque.
 */
public enum LegalContext {
    /** Achat d'un forfait du simulateur. */
    SIMULATOR_ORDER(CGU, CGV, PRIVACY),
    /** Candidature à une formation. */
    REGISTRATION(CGU, CGV, PRIVACY),
    /** Demande de coaching (le prix n'est pas encore connu : les conditions de vente sont acceptées au paiement). */
    COACHING_REQUEST(CGU, PRIVACY),
    /** Page de paiement d'un coaching validé. */
    COACHING_PAYMENT(CGV),
    /** Première connexion d'un apprenant (et à chaque nouvelle version) : usage de la plateforme et des contenus protégés. */
    LEARNER_ACCESS(CGU, PRIVACY);

    private final List<LegalCode> codes;

    LegalContext(LegalCode... codes) {
        this.codes = List.of(codes);
    }

    public List<LegalCode> codes() {
        return codes;
    }
}
