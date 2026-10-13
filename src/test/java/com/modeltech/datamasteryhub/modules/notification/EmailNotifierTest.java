package com.modeltech.datamasteryhub.modules.notification;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.notification.service.CertificateNotice;
import com.modeltech.datamasteryhub.modules.notification.service.EmailNotifier;
import com.modeltech.datamasteryhub.modules.notification.service.InvoiceNotice;
import com.modeltech.datamasteryhub.modules.notification.service.PaymentNotice;
import com.modeltech.datamasteryhub.modules.notification.service.ResilientMailSender;
import com.modeltech.datamasteryhub.modules.notification.service.TemplatedMailer;
import com.modeltech.datamasteryhub.modules.notification.template.EmailRenderer;
import com.modeltech.datamasteryhub.modules.notification.template.MailParts;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chaque e-mail transactionnel : bon destinataire, bon type de journal, HTML + texte, pas de champ vide
 * affiché. Écrit aussi un aperçu HTML de chacun dans {@code target/email-previews/} pour relecture visuelle.
 */
class EmailNotifierTest {

    private static final Path PREVIEWS = Path.of("target", "email-previews");

    private ResilientMailSender sender;
    private EmailNotifier notifier;
    private final List<MimeMessage> sent = new ArrayList<>();
    private final List<String> types = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sender = mock(ResilientMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        when(sender.fromAddress()).thenReturn("noreply@model-technologie.com");
        when(sender.fromName()).thenReturn("Model Technologie");
        when(sender.send(any(MimeMessage.class), anyString())).thenAnswer(i -> {
            sent.add(i.getArgument(0));
            types.add(i.getArgument(1));
            return true;
        });
        EmailRenderer renderer = new EmailRenderer(new EmailRenderer.Brand(
                "Model Technologie", "https://staging.model-technologie.com", "https://staging.model-technologie.com/logo.png",
                "129 Liberté 6 extension, Dakar", "+221 78 862 52 38", "contact@model-technologie.com", "https://www.model-technologie.com"));
        notifier = new EmailNotifier(new TemplatedMailer(sender, renderer));
        ReflectionTestUtils.setField(notifier, "internalEmail", "equipe@model-technologie.com");
        ReflectionTestUtils.setField(notifier, "frontendUrl", "https://staging.model-technologie.com");
    }

    private PaymentNotice payment(String reason) {
        return new PaymentNotice("awa@example.com", "Awa", "Diop", "Power BI — Tableaux de bord", "Cohorte 3 — Novembre 2026", 150_000, "XOF",
                1, 2, LocalDate.of(2026, 11, 5), "https://staging.model-technologie.com/paiement/Xk2f9", "WAVE", "TXN-4821", reason);
    }

    private MailParts last() throws Exception {
        return MailParts.of(sent.get(sent.size() - 1));
    }

    private void preview(String name) throws Exception {
        Files.createDirectories(PREVIEWS);
        Files.writeString(PREVIEWS.resolve(name + ".html"), last().html, StandardCharsets.UTF_8);
    }

    @Test
    void paymentLink_andItsReminder() throws Exception {
        notifier.sendPaymentLinkEmail(payment(null), false);
        assertThat(types).containsExactly("PAYMENT_LINK");
        assertThat(sent.get(0).getAllRecipients()[0].toString()).isEqualTo("awa@example.com");
        assertThat(sent.get(0).getSubject()).isEqualTo("Paiement de votre inscription — Power BI — Tableaux de bord");
        MailParts mail = last();
        assertThat(mail.html).contains("Votre candidature est acceptée").contains("150&nbsp;000 FCFA".replace("&nbsp;", " "))
                .contains("1 sur 2").contains("5 novembre 2026").contains("/paiement/Xk2f9").contains("Ce lien est personnel");
        assertThat(mail.text).contains("Montant à régler : 150 000 FCFA").contains("https://staging.model-technologie.com/paiement/Xk2f9");
        preview("paiement-lien");

        notifier.sendPaymentLinkEmail(payment(null), true);
        assertThat(sent.get(1).getSubject()).startsWith("Rappel : paiement de votre inscription");
        assertThat(last().html).contains("Votre paiement n&#39;a pas encore été reçu");
        preview("paiement-rappel");
    }

    @Test
    void paymentRejected_showsTheReasonAsAQuote() throws Exception {
        notifier.sendPaymentRejectedEmail(payment("Montant reçu inférieur à l'échéance"));
        MailParts mail = last();
        assertThat(types).containsExactly("PAYMENT_REJECTED");
        assertThat(mail.html).contains("Montant reçu inférieur à l&#39;échéance").contains("Déclarer à nouveau mon paiement");
        assertThat(mail.text).contains("> Montant reçu inférieur à l'échéance");
        preview("paiement-refuse");
    }

    @Test
    void internalNotifications_goToTheTeam_withReplyToTheSender() throws Exception {
        Registration reg = new Registration();
        reg.setFirstName("Awa");
        reg.setLastName("Diop");
        reg.setEmail("awa@example.com");
        reg.setPhone("+221 77 000 00 00");
        reg.setCompany("ACME SA");
        reg.setBootcampTitle("Power BI — Tableaux de bord");
        reg.setSessionName("Cohorte 3");
        reg.setPromoCodeUsed("AMIS10");
        reg.setDiscountPercent(10);
        reg.setMessage("Je souhaite financer via mon employeur.\nPouvez-vous m'envoyer le programme ?");
        notifier.send(reg);
        assertThat(types).containsExactly("INTERNAL_REGISTRATION");
        assertThat(sent.get(0).getAllRecipients()[0].toString()).isEqualTo("equipe@model-technologie.com");
        assertThat(sent.get(0).getReplyTo()[0].toString()).isEqualTo("awa@example.com");
        assertThat(last().html).contains("AMIS10 (−10 %)").contains("/admin/candidatures").doesNotContain("Pays");
        preview("interne-candidature");

        ContactMessage contact = new ContactMessage();
        contact.setFirstName("Moussa");
        contact.setLastName("Ba");
        contact.setEmail("moussa@example.com");
        contact.setSubject("Formation en entreprise");
        contact.setMessage("Bonjour,\nNous voudrions former 12 collaborateurs.");
        notifier.sendContactMessage(contact);
        assertThat(types).contains("INTERNAL_CONTACT");
        assertThat(last().html).contains("Formation en entreprise").contains("/admin/messages");
        preview("interne-contact");

        notifier.sendPaymentDeclaredInternal(payment(null));
        assertThat(types).contains("INTERNAL_PAYMENT_DECLARED");
        assertThat(sent.get(2).getSubject()).isEqualTo("Paiement à confirmer — Awa Diop — 150 000 FCFA");
        assertThat(last().html).contains("TXN-4821").contains("/admin/paiements");
        preview("interne-paiement-declare");
    }

    @Test
    void internalNotifications_areSkippedWithoutATeamAddress() {
        ReflectionTestUtils.setField(notifier, "internalEmail", "");
        notifier.sendPaymentDeclaredInternal(payment(null));
        assertThat(sent).isEmpty();
    }

    @Test
    void certificate_andInvoice_carryTheirPdf() throws Exception {
        notifier.sendCertificateReadyEmail(new CertificateNotice("awa@example.com", "Awa", "Power BI — Tableaux de bord", "MT-2026-PB-00012",
                "https://staging.model-technologie.com/certificats/MT-2026-PB-00012", "https://www.linkedin.com/profile/add?name=Power+BI", new byte[]{1, 2, 3}));
        assertThat(types).containsExactly("CERTIFICATE_READY");
        MailParts cert = last();
        assertThat(cert.attachments).containsExactly("Certificat-MT-2026-PB-00012.pdf");
        assertThat(cert.html).contains("Ajouter à mon profil LinkedIn").contains("MT-2026-PB-00012");
        preview("certificat");

        notifier.sendInvoiceEmail(new InvoiceNotice("rh@acme.com", "ACME SA", "FAC-2026-00007", 450_000, "XOF", LocalDate.of(2026, 12, 1), new byte[]{1}));
        MailParts invoice = last();
        assertThat(invoice.attachments).containsExactly("Facture-FAC-2026-00007.pdf");
        assertThat(invoice.html).contains("450 000 FCFA").contains("1 décembre 2026");
        preview("facture");
    }

    @Test
    void accountMails_passwordReset_invitations_newsletter_enrollment() throws Exception {
        notifier.sendPasswordResetEmail("awa@example.com", "https://staging.model-technologie.com/reinitialiser-mot-de-passe?token=t1", 15);
        assertThat(last().html).contains("Réinitialisez votre mot de passe").contains("15 minutes").contains("Bonjour,");
        preview("mot-de-passe-oublie");

        notifier.sendAccountInvitationEmail("awa@example.com", "Awa", "https://staging.model-technologie.com/reinitialiser-mot-de-passe?token=t2", 72, true);
        assertThat(last().html).contains("Bienvenue sur votre espace apprenant").contains("72 heures").contains("Mot de passe oublié");
        preview("invitation-apprenant");

        notifier.sendAccountInvitationEmail("staff@model-technologie.com", "Cheikh", "https://staging.model-technologie.com/admin/reset-password?token=t3", 72, false);
        assertThat(last().html).contains("Votre compte du back-office").doesNotContain("Mot de passe oublié");
        preview("invitation-backoffice");

        notifier.sendNewsletterConfirmationEmail("lecteur@example.com", "https://staging.model-technologie.com/newsletter/confirmation?token=t4", 7);
        assertThat(last().html).contains("Confirmez votre inscription").contains("7 jours");
        preview("newsletter-confirmation");

        Registration reg = new Registration();
        reg.setFirstName("Awa");
        reg.setEmail("awa@example.com");
        reg.setBootcampTitle("Power BI — Tableaux de bord");
        reg.setSessionName("Cohorte 3");
        notifier.sendEnrollmentConfirmedEmail(reg);
        assertThat(types).contains("ENROLLMENT_CONFIRMED");
        assertThat(last().html).contains("Votre inscription est confirmée").contains("/espace");
        preview("inscription-confirmee");

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).send(any(MimeMessage.class), typeCaptor.capture());
        assertThat(typeCaptor.getAllValues()).contains("PASSWORD_RESET", "ACCOUNT_INVITATION", "NEWSLETTER_CONFIRMATION");
    }
}
