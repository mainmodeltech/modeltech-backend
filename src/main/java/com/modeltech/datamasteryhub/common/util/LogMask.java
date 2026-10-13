package com.modeltech.datamasteryhub.common.util;

/** Masque les données personnelles dans les journaux : {@code awa.diop@gmail.com} → {@code aw***@gmail.com}. */
public final class LogMask {

    private LogMask() {}

    public static String email(String email) {
        if (email == null || email.isBlank()) return "?";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }
}
