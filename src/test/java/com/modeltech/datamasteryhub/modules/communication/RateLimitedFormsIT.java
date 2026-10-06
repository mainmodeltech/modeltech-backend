package com.modeltech.datamasteryhub.modules.communication;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.communication.repository.ContactMessageRepository;
import com.modeltech.datamasteryhub.modules.communication.repository.NewsletterSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les formulaires publics répondent 429 (JSON) et n'écrivent rien quand la limite par IP est atteinte. */
class RateLimitedFormsIT extends AbstractIntegrationTest {

    @MockBean private IpRateLimiter rateLimiter;
    @Autowired private ContactMessageRepository contactMessageRepository;
    @Autowired private NewsletterSubscriptionRepository newsletterRepository;

    private static final String TOO_MANY = "Trop de tentatives. Veuillez réessayer dans une heure.";

    private void limitReachedFor(String scope) {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY))
                .when(rateLimiter).check(any(), eq(scope));
    }

    @Test
    void contact_isRateLimited() throws Exception {
        limitReachedFor("contact");

        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"email\":\"a@b.sn\",\"message\":\"m\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(TOO_MANY));

        assertThat(contactMessageRepository.count()).isZero();
        verify(notificationService, never()).notifyNewContactMessage(any());
    }

    @Test
    void diagnosticAndPartnerApplication_areRateLimited() throws Exception {
        limitReachedFor("diagnostic");
        limitReachedFor("partner-application");

        mockMvc.perform(post("/api/v1/diagnostic-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"email\":\"a@b.sn\",\"company\":\"C\",\"role\":\"R\","
                                + "\"peopleCount\":\"RANGE_1_5\",\"need\":\"EXCEL_UPGRADE\"}"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/v1/partner-applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"email\":\"a@b.sn\",\"domain\":\"OTHER\",\"proposal\":\"p\"}"))
                .andExpect(status().isTooManyRequests());

        assertThat(contactMessageRepository.count()).isZero();
    }

    @Test
    void newsletter_isRateLimited_onSubscribeConfirmAndUnsubscribe() throws Exception {
        limitReachedFor("newsletter");

        mockMvc.perform(post("/api/v1/newsletter/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.sn\"}"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/v1/newsletter/subscriptions/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/v1/newsletter/subscriptions/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isTooManyRequests());

        assertThat(newsletterRepository.count()).isZero();
    }
}
