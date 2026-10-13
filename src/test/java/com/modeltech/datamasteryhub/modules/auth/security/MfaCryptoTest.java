package com.modeltech.datamasteryhub.modules.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Protection des secrets MFA au repos : chiffrement authentifié, empreintes à clé, jamais de clé déduite en production. */
class MfaCryptoTest {

    private static final String JWT = "x".repeat(80);
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static MockEnvironment env(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void encryptsAndDecrypts_withAFreshIvEachTime() {
        MfaCrypto crypto = new MfaCrypto(KEY, JWT, env("prod"));

        String a = crypto.encrypt("JBSWY3DPEHPK3PXP");
        String b = crypto.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(a).isNotEqualTo(b).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(crypto.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(crypto.decrypt(b)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void aTamperedOrForeignCiphertextIsRefused() {
        MfaCrypto crypto = new MfaCrypto(KEY, JWT, env("prod"));
        String encrypted = crypto.encrypt("secret");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);

        MfaCrypto other = new MfaCrypto(Base64.getEncoder().encodeToString("y".repeat(32).getBytes()), JWT, env("prod"));
        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recoveryCodeFingerprintsAreKeyed() {
        MfaCrypto a = new MfaCrypto(KEY, JWT, env("prod"));
        MfaCrypto b = new MfaCrypto(Base64.getEncoder().encodeToString("y".repeat(32).getBytes()), JWT, env("prod"));

        assertThat(a.hmac("ABCDE12345")).hasSize(64).isEqualTo(a.hmac("ABCDE12345")).isNotEqualTo(b.hmac("ABCDE12345"));
    }

    @Test
    void inProductionAndStaging_withoutAKey_mfaIsUnavailable_andTheJwtSecretIsNeverUsedAsAKey() {
        assertThat(new MfaCrypto("", JWT, env("prod")).available()).isFalse();
        assertThat(new MfaCrypto("", JWT, env("staging")).available()).isFalse();
        assertThat(new MfaCrypto("", JWT, env("prod")).available()).isFalse();
        assertThatThrownBy(() -> new MfaCrypto("", JWT, env("prod")).encrypt("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inDevelopmentAndTests_aDevelopmentKeyIsDerivedSoLocalWorkNeedsNoSetup() {
        MfaCrypto dev = new MfaCrypto("", JWT, env("dev"));

        assertThat(dev.available()).isTrue();
        assertThat(dev.decrypt(dev.encrypt("secret"))).isEqualTo("secret");
    }

    @Test
    void aTooShortConfiguredKeyDisablesMfaInsteadOfWeakeningIt() {
        assertThat(new MfaCrypto(Base64.getEncoder().encodeToString(new byte[16]), JWT, env("dev")).available()).isFalse();
    }
}
