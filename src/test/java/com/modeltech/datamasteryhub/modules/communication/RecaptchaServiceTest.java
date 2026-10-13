package com.modeltech.datamasteryhub.modules.communication;

import com.modeltech.datamasteryhub.modules.communication.service.impl.RecaptchaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** reCAPTCHA : échec = refus, action et nom d'hôte contrôlés. */
class RecaptchaServiceTest {

    private RestTemplate http;
    private RecaptchaServiceImpl service;

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        service = new RecaptchaServiceImpl(http);
        ReflectionTestUtils.setField(service, "secretKey", "secret");
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "allowedHostnames", List.of());
    }

    private void googleReplies(Map<String, Object> body) {
        when(http.postForObject(any(String.class), any(), eq(Map.class))).thenReturn(body);
    }

    @Test
    void acceptsAHighScoreForTheExpectedAction() {
        googleReplies(Map.of("success", true, "score", 0.9, "action", "bootcamp_register", "hostname", "model-technologie.com"));
        assertThat(service.verify("jeton", "bootcamp_register")).isTrue();
    }

    @Test
    void refusesATokenIssuedForAnotherAction() {
        googleReplies(Map.of("success", true, "score", 0.9, "action", "masterclass_register"));
        assertThat(service.verify("jeton", "bootcamp_register")).isFalse();
    }

    @Test
    void refusesALowScoreOrAFailedVerification() {
        googleReplies(Map.of("success", true, "score", 0.2, "action", "bootcamp_register"));
        assertThat(service.verify("jeton", "bootcamp_register")).isFalse();
        googleReplies(Map.of("success", false, "error-codes", List.of("invalid-input-response")));
        assertThat(service.verify("jeton", "bootcamp_register")).isFalse();
    }

    @Test
    void checksTheHostnameOnlyWhenConfigured() {
        googleReplies(Map.of("success", true, "score", 0.9, "action", "bootcamp_register", "hostname", "evil.example"));
        assertThat(service.verify("jeton", "bootcamp_register")).isTrue();   // pas de liste : pas de contrôle

        ReflectionTestUtils.setField(service, "allowedHostnames", List.of("model-technologie.com", "staging.model-technologie.com"));
        assertThat(service.verify("jeton", "bootcamp_register")).isFalse();
        googleReplies(Map.of("success", true, "score", 0.9, "action", "bootcamp_register", "hostname", "staging.model-technologie.com"));
        assertThat(service.verify("jeton", "bootcamp_register")).isTrue();
    }

    @Test
    void failsClosedWhenGoogleIsUnreachableOrReturnsNothing() {
        when(http.postForObject(any(String.class), any(), eq(Map.class))).thenThrow(new RestClientException("délai dépassé"));
        assertThat(service.verify("jeton", "bootcamp_register")).isFalse();

        googleReplies(null);
        assertThat(service.verify("jeton", "bootcamp_register")).isFalse();
    }

    @Test
    void refusesAnEmptyToken_andSkipsEverythingWhenDisabled() {
        assertThat(service.verify("", "bootcamp_register")).isFalse();
        assertThat(service.verify(null, "bootcamp_register")).isFalse();

        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.verify(null, "bootcamp_register")).isTrue();
    }
}
