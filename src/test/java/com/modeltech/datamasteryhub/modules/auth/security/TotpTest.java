package com.modeltech.datamasteryhub.modules.auth.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TOTP (RFC 6238) et Base32 (RFC 4648) : vecteurs de test officiels. */
class TotpTest {

    /** Secret ASCII des vecteurs de test de la RFC 6238 (annexe B). */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheOfficialRfc6238Vectors_onSixDigits() {
        // La RFC donne 8 chiffres ; les 6 derniers sont les codes attendus par les applications d'authentification
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(59))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1111111109L))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1111111111L))).isEqualTo("050471");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1234567890L))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(2000000000L))).isEqualTo("279037");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(20000000000L))).isEqualTo("353130");
    }

    @Test
    void acceptsTheCurrentCode_andOneStepOfClockDrift_butNotMore() {
        long now = 1_700_000_000L;
        long step = Totp.stepAt(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, -1)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, -1)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, -1)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, -1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, -1)).isEmpty();
    }

    @Test
    void aCodeAlreadyUsedCannotBeReplayed_evenInsideTheWindow() {
        long now = 1_700_000_000L;
        long step = Totp.stepAt(now);
        OptionalLong first = Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, -1);
        assertThat(first).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, first.getAsLong())).isEmpty();
        // le code du pas suivant, lui, reste accepté
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, first.getAsLong())).hasValue(step + 1);
    }

    @Test
    void refusesMalformedCodes() {
        long now = 1_700_000_000L;
        for (String bad : new String[]{null, "", "12345", "1234567", "abcdef", "12 345", "１２３４５６"}) {
            assertThat(Totp.verify(RFC_SECRET, bad, now, -1)).as("code « %s »", bad).isEmpty();
        }
    }

    @Test
    void secretsAreRandom_andTheUriCarriesWhatAuthenticatorAppsNeed() {
        byte[] a = Totp.newSecret();
        byte[] b = Totp.newSecret();
        assertThat(a).hasSize(20).isNotEqualTo(b);

        String uri = Totp.uri("Model Technologie", "awa@example.com", RFC_SECRET);
        assertThat(uri).startsWith("otpauth://totp/Model%20Technologie:awa%40example.com?")
                .contains("secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
                .contains("issuer=Model%20Technologie").contains("digits=6").contains("period=30").contains("algorithm=SHA1");
    }

    @Test
    void base32_roundTrips_andMatchesRfc4648() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(new String(Base32.decode("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("mzxw 6ytb-oi=="), StandardCharsets.US_ASCII)).isEqualTo("foobar");   // casse, espaces, tirets, remplissage tolérés
        byte[] secret = Totp.newSecret();
        assertThat(Base32.decode(Base32.encode(secret))).isEqualTo(secret);
        assertThatThrownBy(() -> Base32.decode("MZXW6!")).isInstanceOf(IllegalArgumentException.class);
    }
}
