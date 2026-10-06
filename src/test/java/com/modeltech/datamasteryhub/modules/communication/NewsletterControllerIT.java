package com.modeltech.datamasteryhub.modules.communication;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.communication.entity.NewsletterSubscription;
import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import com.modeltech.datamasteryhub.modules.communication.repository.NewsletterSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/v1/newsletter/subscriptions (double opt-in) et /api/v1/admin/newsletter-subscriptions. */
class NewsletterControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/newsletter/subscriptions";
    private static final String SAME_PUBLIC_MESSAGE =
            "Si cette adresse est valide, un e-mail de confirmation vient de vous être envoyé.";

    @Autowired private NewsletterSubscriptionRepository repository;

    // ── Abonnement ───────────────────────────────────────────────────

    @Test
    void subscribe_createsAPendingSubscription_andSendsTheConfirmationLink() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Awa.Ndiaye@Exemple.SN\",\"source\":\"ressources\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(SAME_PUBLIC_MESSAGE));

        NewsletterSubscription saved = repository.findByEmail("awa.ndiaye@exemple.sn").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NewsletterStatus.PENDING);
        assertThat(saved.getSource()).isEqualTo("ressources");
        assertThat(saved.getConfirmationToken()).hasSize(43);
        assertThat(saved.getUnsubscribeToken()).hasSize(43).isNotEqualTo(saved.getConfirmationToken());
        assertThat(saved.getConfirmationExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendNewsletterConfirmationEmail(
                eq("awa.ndiaye@exemple.sn"), link.capture(), eq(7));
        assertThat(link.getValue()).endsWith("/newsletter/confirmation?token=" + saved.getConfirmationToken());
    }

    @Test
    void subscribe_validatesTheEmail() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"pas-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
        verify(notificationService, never()).sendNewsletterConfirmationEmail(anyString(), anyString(), anyInt());
    }

    @Test
    void subscribe_isIdenticalForAnAlreadyConfirmedAddress_andSendsNothing() throws Exception {
        confirmedSubscription("deja@exemple.sn");
        clearInvocations(notificationService); // l'e-mail de la préparation ne compte pas

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"deja@exemple.sn\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(SAME_PUBLIC_MESSAGE));

        verify(notificationService, never()).sendNewsletterConfirmationEmail(anyString(), anyString(), anyInt());
        assertThat(repository.findByEmail("deja@exemple.sn").orElseThrow().getStatus())
                .isEqualTo(NewsletterStatus.CONFIRMED);
    }

    @Test
    void subscribe_again_whilePending_issuesANewLinkAndInvalidatesTheOldOne() throws Exception {
        subscribe("relance@exemple.sn");
        String oldToken = repository.findByEmail("relance@exemple.sn").orElseThrow().getConfirmationToken();

        subscribe("relance@exemple.sn");
        String newToken = repository.findByEmail("relance@exemple.sn").orElseThrow().getConfirmationToken();

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(repository.count()).isEqualTo(1);
        verify(notificationService, times(2)).sendNewsletterConfirmationEmail(eq("relance@exemple.sn"), anyString(), eq(7));
        confirm(oldToken, 400);
        confirm(newToken, 200);
    }

    // ── Confirmation ─────────────────────────────────────────────────

    @Test
    void confirm_activatesTheSubscription_andIsIdempotent() throws Exception {
        subscribe("confirme@exemple.sn");
        String token = repository.findByEmail("confirme@exemple.sn").orElseThrow().getConfirmationToken();

        mockMvc.perform(post(URL + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        NewsletterSubscription confirmed = repository.findByEmail("confirme@exemple.sn").orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(NewsletterStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();

        confirm(token, 200); // double clic sur le lien
        assertThat(repository.findByEmail("confirme@exemple.sn").orElseThrow().getStatus())
                .isEqualTo(NewsletterStatus.CONFIRMED);
    }

    @Test
    void confirm_rejectsUnknownBlankAndExpiredTokens() throws Exception {
        confirm("jeton-inconnu", 400);
        mockMvc.perform(post(URL + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());

        subscribe("expire@exemple.sn");
        NewsletterSubscription s = repository.findByEmail("expire@exemple.sn").orElseThrow();
        s.setConfirmationExpiresAt(LocalDateTime.now().minusMinutes(1));
        repository.save(s);

        mockMvc.perform(post(URL + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + s.getConfirmationToken() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Lien de confirmation invalide ou expiré."));
        assertThat(repository.findByEmail("expire@exemple.sn").orElseThrow().getStatus())
                .isEqualTo(NewsletterStatus.PENDING);
    }

    // ── Désinscription ───────────────────────────────────────────────

    @Test
    void unsubscribe_deactivates_isIdempotent_andAnOldConfirmationLinkDoesNotResubscribe() throws Exception {
        subscribe("partant@exemple.sn");
        String confirmationToken = repository.findByEmail("partant@exemple.sn").orElseThrow().getConfirmationToken();
        confirm(confirmationToken, 200);
        String unsubscribeToken = repository.findByEmail("partant@exemple.sn").orElseThrow().getUnsubscribeToken();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(URL + "/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + unsubscribeToken + "\"}"))
                    .andExpect(status().isOk());
        }
        NewsletterSubscription after = repository.findByEmail("partant@exemple.sn").orElseThrow();
        assertThat(after.getStatus()).isEqualTo(NewsletterStatus.UNSUBSCRIBED);
        assertThat(after.getUnsubscribedAt()).isNotNull();

        confirm(confirmationToken, 400); // l'ancien lien de confirmation ne réabonne pas
        assertThat(repository.findByEmail("partant@exemple.sn").orElseThrow().getStatus())
                .isEqualTo(NewsletterStatus.UNSUBSCRIBED);
    }

    @Test
    void unsubscribe_rejectsAnUnknownToken() throws Exception {
        mockMvc.perform(post(URL + "/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"inconnu\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Lien de désinscription invalide."));
    }

    @Test
    void resubscribe_afterUnsubscribe_requiresANewConfirmation() throws Exception {
        confirmedSubscription("revenu@exemple.sn");
        clearInvocations(notificationService);
        String unsubscribeToken = repository.findByEmail("revenu@exemple.sn").orElseThrow().getUnsubscribeToken();
        mockMvc.perform(post(URL + "/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + unsubscribeToken + "\"}")).andExpect(status().isOk());

        subscribe("revenu@exemple.sn");

        NewsletterSubscription s = repository.findByEmail("revenu@exemple.sn").orElseThrow();
        assertThat(s.getStatus()).isEqualTo(NewsletterStatus.PENDING);
        assertThat(s.getUnsubscribedAt()).isNull();
        verify(notificationService).sendNewsletterConfirmationEmail(eq("revenu@exemple.sn"), anyString(), eq(7));
    }

    // ── Admin ────────────────────────────────────────────────────────

    @Test
    void admin_listsSubscribersFilteredByStatus_andNeverExposesTokens() throws Exception {
        confirmedSubscription("a@exemple.sn");
        subscribe("b@exemple.sn");

        mockMvc.perform(get("/api/v1/admin/newsletter-subscriptions").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));

        mockMvc.perform(get("/api/v1/admin/newsletter-subscriptions").param("status", "CONFIRMED").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("a@exemple.sn"))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].confirmationToken").doesNotExist())
                .andExpect(jsonPath("$.data[0].unsubscribeToken").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/newsletter-subscriptions").param("status", "NOPE").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/newsletter-subscriptions")).andExpect(status().isForbidden());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void subscribe(String email) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());
    }

    private void confirm(String token, int expectedStatus) throws Exception {
        mockMvc.perform(post(URL + "/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().is(expectedStatus));
    }

    private void confirmedSubscription(String email) throws Exception {
        subscribe(email);
        NewsletterSubscription s = repository.findByEmail(email).orElseThrow();
        confirm(s.getConfirmationToken(), 200);
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.local").roles("ADMIN");
    }
}
