package com.modeltech.datamasteryhub.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitaires de IpRateLimiter (sans contexte Spring ni Docker). */
class IpRateLimiterTest {

    @Test
    void allowsUpToTheLimit_thenRejectsWith429() {
        IpRateLimiter limiter = new IpRateLimiter(3);
        MockHttpServletRequest request = requestFrom("10.0.0.1");

        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> limiter.check(request, "contact")).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.check(request, "contact"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(429);
                    assertThat(e.getReason()).isEqualTo("Trop de tentatives. Veuillez réessayer dans une heure.");
                });
    }

    @Test
    void limitsAreIndependentPerIpAndPerScope() {
        IpRateLimiter limiter = new IpRateLimiter(1);

        assertThat(limiter.tryAcquire("contact", "10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("contact", "10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("contact", "10.0.0.2")).isTrue();      // autre IP
        assertThat(limiter.tryAcquire("newsletter", "10.0.0.1")).isTrue();   // autre formulaire
    }

    @Test
    void clientIp_usesTheLastForwardedHop_notTheOneTheClientCanForge() {
        MockHttpServletRequest request = requestFrom("172.18.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");

        assertThat(IpRateLimiter.clientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void forgingTheFirstForwardedHopDoesNotBypassTheLimit() {
        IpRateLimiter limiter = new IpRateLimiter(1);

        MockHttpServletRequest first = requestFrom("172.18.0.1");
        first.addHeader("X-Forwarded-For", "9.9.9.1, 203.0.113.9");
        MockHttpServletRequest second = requestFrom("172.18.0.1");
        second.addHeader("X-Forwarded-For", "9.9.9.2, 203.0.113.9"); // même vraie IP, faux « client » différent

        limiter.check(first, "contact");
        assertThatThrownBy(() -> limiter.check(second, "contact")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void clientIp_fallsBackToTheRemoteAddress() {
        assertThat(IpRateLimiter.clientIp(requestFrom("198.51.100.7"))).isEqualTo("198.51.100.7");
    }

    private static MockHttpServletRequest requestFrom(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void loginHasItsOwnBudget_independentFromTheForms() {
        IpRateLimiter limiter = new IpRateLimiter(10, 2);
        MockHttpServletRequest request = requestFrom("10.0.0.9");

        assertThatCode(() -> limiter.check(request, IpRateLimiter.LOGIN_SCOPE)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(request, IpRateLimiter.LOGIN_SCOPE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(request, IpRateLimiter.LOGIN_SCOPE))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(429));

        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> limiter.check(request, "contact")).doesNotThrowAnyException();
        }
    }
}
