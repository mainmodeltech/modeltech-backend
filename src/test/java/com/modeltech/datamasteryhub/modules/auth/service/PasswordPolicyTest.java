package com.modeltech.datamasteryhub.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Politique des mots de passe : longueur, mots courants et variantes évidentes, adresse e-mail. */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    PasswordPolicyTest() {
        ReflectionTestUtils.setField(policy, "minLengthStaff", 12);
        ReflectionTestUtils.setField(policy, "minLengthLearner", 10);
    }

    private String violation(String password, boolean staff) {
        return policy.violation(password, "awa.diop@example.com", staff);
    }

    @Test
    void staffNeedTwelveCharacters_learnersTen() {
        assertThat(violation("Cormoran-2026", true)).isNull();            // 13
        assertThat(violation("Cormoran-26", true)).contains("au moins 12");     // 11
        assertThat(violation("Cormoran-26", false)).isNull();                    // 11 ≥ 10
        assertThat(violation("Cormoran-", false)).contains("au moins 10");       // 9
    }

    @Test
    void refusesCommonPasswordsAndTheirObviousVariants() {
        for (String weak : new String[]{
                "password1234", "Password123!!", "P@ssw0rd2026!", "azertyuiop12", "AZERTYazerty", "motdepasse123", "MotDePasse#2026",
                "123456789012", "qwertyuiop123", "bienvenue2026!", "modeltechnologie1", "Dakar2026!!!!", "senegal221221"}) {
            assertThat(violation(weak, true)).as(weak).contains("trop courant");
        }
    }

    @Test
    void refusesRepeatedCharactersAndSequences() {
        assertThat(violation("aaaaaaaaaaaaaa", true)).contains("trop courant");
        assertThat(violation("abababababab", true)).contains("trop courant");
        assertThat(violation("abcdefghijklm", true)).contains("trop courant");
        assertThat(violation("987654321098", true)).contains("trop courant");
    }

    @Test
    void refusesAPasswordContainingTheEmailAddress() {
        assertThat(policy.violation("xx-awa.diop-2026-xx", "awa.diop@example.com", true)).contains("adresse e-mail");
        assertThat(policy.violation("Cormoran-2026", "awa.diop@example.com", true)).isNull();
    }

    @Test
    void acceptsLongPassphrases_withoutCompositionRules() {
        assertThat(violation("correct horse battery staple", true)).isNull();
        assertThat(violation("le chat dort sur le canape bleu", true)).isNull();
        assertThat(violation("vingt-trois-bananes-vertes", true)).isNull();
        assertThat(violation("mon-mot-de-passe-est-un-secret-bien-gardé", true)).isNull();   // contient un mot courant mais bien plus long
    }

    @Test
    void refusesMoreThanSeventyTwoCharacters_whichBcryptWouldSilentlyTruncate() {
        assertThat(violation("a1b2".repeat(19), true)).contains("72");
        assertThat(violation("Zx9-".repeat(18), true)).isNull();   // exactement 72
    }

    @Test
    void validateRaisesA400WithTheReason() {
        assertThatThrownBy(() -> policy.validate("court", "awa@example.com", true))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(400);
                    assertThat(e.getReason()).contains("au moins 12");
                });
        policy.validate("Cormoran-Biscuit-2026", "awa@example.com", true);
    }
}
