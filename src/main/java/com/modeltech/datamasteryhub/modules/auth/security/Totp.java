package com.modeltech.datamasteryhub.modules.auth.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.OptionalLong;

/**
 * Mots de passe à usage unique basés sur le temps (RFC 6238 : HMAC-SHA1, 6 chiffres, pas de 30 s), compatibles
 * avec Google Authenticator, Microsoft Authenticator, Authy, 1Password, Bitwarden…
 */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    /** Tolérance d'horloge : le pas précédent et le suivant sont acceptés. */
    private static final int WINDOW = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** 160 bits aléatoires (taille recommandée par la RFC 4226). */
    public static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static long stepAt(long epochSeconds) {
        return Math.floorDiv(epochSeconds, STEP_SECONDS);
    }

    /** Code à 6 chiffres du pas donné. */
    public static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] counter = new byte[8];
            for (int i = 7; i >= 0; i--) {
                counter[i] = (byte) (step >>> (8 * (7 - i)));
            }
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 indisponible", e);
        }
    }

    /**
     * Vérifie un code saisi à l'instant donné.
     *
     * @param lastAcceptedStep dernier pas déjà accepté pour ce compte : un pas déjà utilisé est refusé
     *                         (un code observé par un tiers ne peut pas être rejoué), -1 si aucun
     * @return le pas qui correspond, ou vide
     */
    public static OptionalLong verify(byte[] secret, String submitted, long epochSeconds, long lastAcceptedStep) {
        if (submitted == null || !submitted.matches("\\d{" + DIGITS + "}")) return OptionalLong.empty();
        long current = stepAt(epochSeconds);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (step <= lastAcceptedStep) continue;
            if (MessageDigest.isEqual(code(secret, step).getBytes(StandardCharsets.US_ASCII), submitted.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** Adresse {@code otpauth://} lue par les applications d'authentification (QR code ou lien). */
    public static String uri(String issuer, String account, byte[] secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + Base32.encode(secret) + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
