package com.modeltech.datamasteryhub.modules.notification;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.notification.entity.EmailLog;
import com.modeltech.datamasteryhub.modules.notification.repository.EmailLogRepository;
import com.modeltech.datamasteryhub.modules.notification.service.ResilientMailSender;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Envoi des e-mails : reprises, journal, redirection hors production et diagnostic depuis le back-office. */
@TestPropertySource(properties = {
        "spring.mail.username=envoyeur@example.com",
        "app.mail.max-attempts=3",
        "app.mail.retry-delay-ms=0"
})
class MailDeliveryIT extends AbstractIntegrationTest {

    @MockBean private JavaMailSenderImpl delegate;

    @Autowired private ResilientMailSender sender;
    @Autowired private EmailLogRepository logs;

    @BeforeEach
    void setUp() {
        reset(delegate);
        ReflectionTestUtils.setField(sender, "redirectTo", "");
        ReflectionTestUtils.setField(sender, "username", "envoyeur@example.com");
    }

    @Test
    void successfulSend_isLogged() {
        assertThat(sender.send(simple("awa@example.com"), "PAYMENT_LINK")).isTrue();

        EmailLog log = onlyLog();
        assertThat(log.getStatus()).isEqualTo("SENT");
        assertThat(log.getType()).isEqualTo("PAYMENT_LINK");
        assertThat(log.getRecipient()).isEqualTo("awa@example.com");
        assertThat(log.getAttempts()).isEqualTo(1);
        assertThat(log.getSentAt()).isNotNull();
        assertThat(log.getError()).isNull();
    }

    @Test
    void transientFailures_areRetried_thenSucceed() {
        doThrow(new MailSendException("connexion perdue")).doThrow(new MailSendException("timeout"))
                .doNothing().when(delegate).send(any(SimpleMailMessage.class));

        assertThat(sender.send(simple("awa@example.com"), "PASSWORD_RESET")).isTrue();

        verify(delegate, times(3)).send(any(SimpleMailMessage.class));
        assertThat(onlyLog().getAttempts()).isEqualTo(3);
        assertThat(onlyLog().getStatus()).isEqualTo("SENT");
    }

    @Test
    void persistentFailure_isGivenUpAfterMaxAttempts_andRecordedWithItsCause() {
        doThrow(new MailSendException("Mail server connection failed", new javax.net.ssl.SSLHandshakeException("PKIX path building failed")))
                .when(delegate).send(any(SimpleMailMessage.class));

        assertThat(sender.send(simple("awa@example.com"), "ACCOUNT_INVITATION")).isFalse();

        verify(delegate, times(3)).send(any(SimpleMailMessage.class));
        EmailLog log = onlyLog();
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getAttempts()).isEqualTo(3);
        assertThat(log.getError()).contains("SSLHandshakeException").contains("PKIX");
    }

    @Test
    void authenticationFailure_isNotRetried() {
        doThrow(new MailAuthenticationException("535 mot de passe refusé")).when(delegate).send(any(SimpleMailMessage.class));

        assertThat(sender.send(simple("awa@example.com"), "PAYMENT_LINK")).isFalse();

        verify(delegate, times(1)).send(any(SimpleMailMessage.class));
        assertThat(onlyLog().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void unconfiguredMailbox_isSkippedAndNeverContactsTheServer() {
        ReflectionTestUtils.setField(sender, "username", "");

        assertThat(sender.send(simple("awa@example.com"), "PAYMENT_LINK")).isFalse();

        verify(delegate, never()).send(any(SimpleMailMessage.class));
        assertThat(onlyLog().getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    void redirect_sendsEverythingToTheTestAddress_andKeepsTheOriginalInTheSubject() throws Exception {
        ReflectionTestUtils.setField(sender, "redirectTo", "recette@example.com");
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        mime.setRecipients(Message.RecipientType.TO, "client@example.com");
        mime.setSubject("Bienvenue");

        assertThat(sender.send(mime, "REGISTRATION_PENDING")).isTrue();
        assertThat(sender.send(simple("autre@example.com"), "PAYMENT_LINK")).isTrue();

        ArgumentCaptor<MimeMessage> sentMime = ArgumentCaptor.forClass(MimeMessage.class);
        verify(delegate).send(sentMime.capture());
        assertThat(sentMime.getValue().getAllRecipients()[0].toString()).isEqualTo("recette@example.com");
        assertThat(sentMime.getValue().getSubject()).isEqualTo("[→ client@example.com] Bienvenue");

        ArgumentCaptor<SimpleMailMessage> sentSimple = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(delegate).send(sentSimple.capture());
        assertThat(sentSimple.getValue().getTo()).containsExactly("recette@example.com");
        assertThat(sentSimple.getValue().getSubject()).startsWith("[→ autre@example.com] ");

        assertThat(logs.findAll()).extracting(EmailLog::getRecipient)
                .containsExactlyInAnyOrder("client@example.com (redirigé)", "autre@example.com (redirigé)");
    }

    // ── Back-office ──────────────────────────────────────────────────

    @Test
    void adminCanBrowseTheLog_filterByStatus_andReadTheMailboxStatus() throws Exception {
        sender.send(simple("ok@example.com"), "PAYMENT_LINK");
        doThrow(new MailSendException("boom")).when(delegate).send(any(SimpleMailMessage.class));
        sender.send(simple("ko@example.com"), "PASSWORD_RESET");

        mockMvc.perform(get("/api/v1/admin/email-logs").with(user("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
        mockMvc.perform(get("/api/v1/admin/email-logs").param("status", "failed").with(user("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].recipient").value("ko@example.com"))
                .andExpect(jsonPath("$.data[0].type").value("PASSWORD_RESET"));
        mockMvc.perform(get("/api/v1/admin/email-logs/status").with(user("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.from").value("envoyeur@example.com"))
                .andExpect(jsonPath("$.data.lastError").value(org.hamcrest.Matchers.containsString("boom")))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void testEmail_isReservedToSuperAdmins_andReportsTheOutcome() throws Exception {
        String body = "{\"to\":\"moi@example.com\"}";
        mockMvc.perform(post("/api/v1/admin/email-logs/test").with(user("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/email-logs").with(user("EDITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/email-logs/test").with(user("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"pas-un-email\"}")).andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/email-logs/test").with(user("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sent").value(true))
                .andExpect(jsonPath("$.data.deliveredTo").value("moi@example.com"));

        doThrow(new MailSendException("serveur injoignable")).when(delegate).send(any(SimpleMailMessage.class));
        mockMvc.perform(post("/api/v1/admin/email-logs/test").with(user("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sent").value(false))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("serveur injoignable")));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private SimpleMailMessage simple(String to) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom("envoyeur@example.com");
        m.setTo(to);
        m.setSubject("Sujet");
        m.setText("Corps");
        return m;
    }

    private EmailLog onlyLog() {
        List<EmailLog> all = logs.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private static RequestPostProcessor user(String role) {
        return SecurityMockMvcRequestPostProcessors.user("staff@test.local").roles(role);
    }
}
