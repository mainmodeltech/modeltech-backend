package com.modeltech.datamasteryhub.modules.simulator.service;

import java.util.Locale;
import java.util.Set;

/** Adresses jetables courantes : refusées pour l'essai gratuit (il viderait la banque) comme pour un achat (aucun suivi possible). */
final class DisposableEmails {

    private static final Set<String> DOMAINS = Set.of("mailinator.com", "yopmail.com", "yopmail.fr", "guerrillamail.com", "sharklasers.com",
            "10minutemail.com", "tempmail.com", "temp-mail.org", "trashmail.com", "getnada.com", "dispostable.com", "maildrop.cc",
            "throwawaymail.com", "fakeinbox.com", "mintemail.com", "mohmal.com");

    private DisposableEmails() {}

    static boolean isDisposable(String email) {
        if (email == null) return false;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return DOMAINS.contains(e.substring(e.indexOf('@') + 1));
    }
}
