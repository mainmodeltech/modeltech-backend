package com.modeltech.datamasteryhub.common.util;

import java.security.SecureRandom;
import java.util.Base64;

/** Génération de jetons opaques aléatoires (liens de confirmation, de désinscription…). */
public final class TokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenUtils() {}

    /** 256 bits aléatoires, encodés en base64url sans padding (43 caractères). */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
