package com.modeltech.datamasteryhub.modules.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Protège les secrets MFA au repos : les secrets TOTP sont chiffrés (AES-256-GCM) et les codes de secours
 * ne sont gardés que sous forme d'empreinte HMAC-SHA256. La clé vient de {@code app.auth.mfa.encryption-key}
 * (32 octets en Base64, {@code openssl rand -base64 32}) : une fuite de la base seule ne livre aucun secret.
 *
 * <p>Hors production (profils autres que {@code prod} et {@code staging}) et si la variable est absente, une clé
 * de développement est dérivée du secret JWT, pour que l'environnement local fonctionne sans configuration.
 * En production/staging sans clé, le MFA est indisponible (les comptes du personnel ne peuvent pas se
 * connecter) et une erreur claire est journalisée au démarrage : on ne dérive jamais la clé du secret JWT, dont la
 * rotation rendrait tous les secrets illisibles.
 */
@Component
@Slf4j
public class MfaCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    public MfaCrypto(@Value("${app.auth.mfa.encryption-key:}") String configuredKey,
                     @Value("${app.jwt.secret:}") String jwtSecret,
                     Environment environment) {
        byte[] resolved = null;
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length < 32) {
                log.error("MFA_ENCRYPTION_KEY trop courte ({} octets, 32 minimum) : le MFA est indisponible. Générer une clé avec `openssl rand -base64 32`.", decoded.length);
            } else {
                resolved = decoded;
            }
        } else if (!environment.acceptsProfiles(Profiles.of("prod", "staging")) && jwtSecret != null && !jwtSecret.isBlank()) {
            resolved = sha256(("mfa-dev-key:" + jwtSecret).getBytes(StandardCharsets.UTF_8));
            log.warn("MFA_ENCRYPTION_KEY absente : clé de développement dérivée du secret JWT (jamais en production).");
        } else {
            log.error("MFA_ENCRYPTION_KEY absente : le MFA est indisponible et les comptes du personnel ne pourront pas se connecter. Générer une clé avec `openssl rand -base64 32`.");
        }
        this.key = resolved;
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, 0, 32, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, 0, 32, "AES"), new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret MFA illisible (clé de chiffrement modifiée ?)", e);
        }
    }

    /** Empreinte à clé secrète (codes de secours). */
    public String hmac(String value) {
        requireKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponible", e);
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("Clé de chiffrement MFA non configurée");
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
