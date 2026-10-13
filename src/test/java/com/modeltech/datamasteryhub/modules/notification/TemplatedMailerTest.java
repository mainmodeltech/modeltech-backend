package com.modeltech.datamasteryhub.modules.notification;

import com.modeltech.datamasteryhub.modules.notification.service.ResilientMailSender;
import com.modeltech.datamasteryhub.modules.notification.service.TemplatedMailer;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import com.modeltech.datamasteryhub.modules.notification.template.EmailRenderer;
import com.modeltech.datamasteryhub.modules.notification.template.MailParts;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Un e-mail = texte + HTML, expéditeur nommé, pièces jointes, adresse de réponse. */
class TemplatedMailerTest {

    private ResilientMailSender sender;
    private TemplatedMailer mailer;

    @BeforeEach
    void setUp() {
        sender = mock(ResilientMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(sender.fromAddress()).thenReturn("noreply@model-technologie.com");
        when(sender.fromName()).thenReturn("Model Technologie");
        when(sender.send(any(MimeMessage.class), any())).thenReturn(true);
        mailer = new TemplatedMailer(sender, new EmailRenderer(new EmailRenderer.Brand(
                "Model Technologie", "https://www.model-technologie.com", "https://www.model-technologie.com/logo.png", "", "", "", "")));
    }

    private MimeMessage sent(String type) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture(), eq(type));
        return captor.getValue();
    }

    @Test
    void sendsAMultipartWithPlainTextAndHtml_fromANamedSender() throws Exception {
        boolean ok = mailer.send("awa@example.com", "Bienvenue", EmailContent.builder("Bienvenue").paragraph("Bonjour **Awa**").build(), "TEST");

        assertThat(ok).isTrue();
        MimeMessage message = sent("TEST");
        assertThat(message.getSubject()).isEqualTo("Bienvenue");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("noreply@model-technologie.com");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Model Technologie");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("awa@example.com");

        MailParts parts = MailParts.of(message);
        assertThat(parts.html).contains("<strong>Awa</strong>").contains("<!DOCTYPE html>");
        assertThat(parts.text).contains("Bonjour Awa").doesNotContain("**");
        assertThat(parts.attachments).isEmpty();
    }

    @Test
    void attachesFiles_andSetsTheReplyTo() throws Exception {
        mailer.send("rh@acme.com", "Facture FAC-1", EmailContent.builder("Facture").paragraph("Ci-joint.").build(), "INVOICE",
                "compta@model-technologie.com", List.of(new TemplatedMailer.Attachment("Facture-FAC-1.pdf", new byte[]{37, 80, 68, 70}, "application/pdf")));

        MimeMessage message = sent("INVOICE");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("compta@model-technologie.com");
        MailParts parts = MailParts.of(message);
        assertThat(parts.attachments).containsExactly("Facture-FAC-1.pdf");
        assertThat(parts.html).isNotNull();
        assertThat(parts.text).isNotNull();
    }

    @Test
    void neverThrows_whenTheMessageCannotBeComposed() {
        assertThat(mailer.send("adresse invalide <<", "Sujet", EmailContent.builder("T").build(), "TEST")).isFalse();
    }
}
