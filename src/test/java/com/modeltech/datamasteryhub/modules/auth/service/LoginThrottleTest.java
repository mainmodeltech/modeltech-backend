package com.modeltech.datamasteryhub.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Frein par couple (e-mail, adresse IP). */
class LoginThrottleTest {

    private LoginThrottle throttle() {
        LoginThrottle t = new LoginThrottle();
        ReflectionTestUtils.setField(t, "maxAttempts", 3);
        ReflectionTestUtils.setField(t, "minutes", 15);
        return t;
    }

    @Test
    void blocksAfterTheLimit_forThatPairOnly() {
        LoginThrottle t = throttle();
        for (int i = 0; i < 2; i++) t.recordFailure("awa@example.com", "10.0.0.1");
        assertThat(t.blockedMinutes("awa@example.com", "10.0.0.1")).isZero();

        t.recordFailure("awa@example.com", "10.0.0.1");
        assertThat(t.blockedMinutes("awa@example.com", "10.0.0.1")).isBetween(1L, 16L);
        assertThat(t.blockedMinutes("awa@example.com", "10.0.0.2")).isZero();      // même e-mail, autre adresse
        assertThat(t.blockedMinutes("autre@example.com", "10.0.0.1")).isZero();    // autre e-mail, même adresse
    }

    @Test
    void isCaseInsensitiveOnTheEmail_andResetClearsTheBlock() {
        LoginThrottle t = throttle();
        for (int i = 0; i < 3; i++) t.recordFailure("Awa@Example.com", "10.0.0.1");
        assertThat(t.blockedMinutes("awa@example.com", "10.0.0.1")).isPositive();

        t.reset("AWA@example.com", "10.0.0.1");
        assertThat(t.blockedMinutes("awa@example.com", "10.0.0.1")).isZero();
    }

    @Test
    void worksForUnknownAddresses_soNothingRevealsWhetherAnAccountExists() {
        LoginThrottle t = throttle();
        for (int i = 0; i < 3; i++) t.recordFailure("personne@example.com", "10.0.0.1");
        assertThat(t.blockedMinutes("personne@example.com", "10.0.0.1")).isPositive();
    }
}
