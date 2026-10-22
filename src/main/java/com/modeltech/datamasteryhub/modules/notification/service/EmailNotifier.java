package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.modeltech.datamasteryhub.modules.notification.template.EmailContent.entry;

/**
 * E-mails transactionnels de la plateforme (candidats, apprenants, personnel). Chaque message est décrit
 * par un {@link EmailContent} puis mis en page par la charte unique (HTML + texte) ; l'envoi passe par
 * {@link TemplatedMailer} (reprises, journal, redirection de recette).
 *
 * <p>Un message qui ne part pas ne fait jamais échouer l'opération métier : l'échec est journalisé.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotifier {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    /** Pages du back-office des propositions de programme (relecture, et « mes propositions » du formateur). */
    private static final String PROPOSALS_PAGE = "/admin/propositions-programme";
    private static final String MY_PROPOSALS_PAGE = "/admin/mes-propositions";

    private final TemplatedMailer mailer;

    /** Adresse de l'équipe : destinataire des notifications internes. */
    @Value("${app.notifications.email.to:admin@model-technologie.com}")
    private String internalEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // =========================================================================
    //  ÉQUIPE — notifications internes
    // =========================================================================

    public void send(Registration r) {
        if (isDisabled()) return;
        String name = fullName(r);
        String promo = r.getPromoCodeUsed() != null && !r.getPromoCodeUsed().isBlank()
                ? r.getPromoCodeUsed() + (r.getDiscountPercent() != null && r.getDiscountPercent() > 0 ? " (−" + r.getDiscountPercent() + " %)" : "")
                : null;
        EmailContent content = EmailContent.builder("Nouvelle candidature")
                .preheader(name + " souhaite rejoindre « " + orDash(r.getBootcampTitle()) + " »")
                .paragraph("**" + name + "** vient de candidater depuis le site.")
                .details(List.of(
                        entry("Formation", r.getBootcampTitle()), entry("Session", r.getSessionName()),
                        entry("E-mail", r.getEmail()), entry("Téléphone", r.getPhone()), entry("Pays", r.getCountry()),
                        entry("Entreprise", r.getCompany()), entry("Poste", r.getPosition()), entry("Code promo", promo)))
                .quote(r.getMessage(), null)
                .button("Ouvrir les candidatures", admin("/admin/candidatures"))
                .noSignature()
                .build();
        mailer.send(internalEmail, "Nouvelle candidature — " + name + " — " + orDash(r.getBootcampTitle()),
                content, "INTERNAL_REGISTRATION", r.getEmail(), List.of());
    }

    public void sendContactMessage(ContactMessage c) {
        if (isDisabled()) return;
        String name = (c.getFirstName() + " " + c.getLastName()).trim();
        String subject = c.getSubject() != null && !c.getSubject().isBlank() ? c.getSubject() : "Sans objet";
        EmailContent content = EmailContent.builder("Nouveau message")
                .preheader(name + " — " + subject)
                .paragraph("**" + name + "** vous a écrit depuis le site. Répondez directement à cet e-mail pour lui répondre.")
                .details(List.of(entry("Sujet", subject), entry("E-mail", c.getEmail()),
                        entry("Téléphone", c.getPhone()), entry("Entreprise", c.getCompany())))
                .quote(c.getMessage(), null)
                .button("Ouvrir les messages", admin("/admin/messages"))
                .noSignature()
                .build();
        mailer.send(internalEmail, "Nouveau message — " + name + " — " + subject, content, "INTERNAL_CONTACT", c.getEmail(), List.of());
    }

    public void sendPaymentDeclaredInternal(PaymentNotice n) {
        if (isDisabled()) return;
        EmailContent content = EmailContent.builder("Paiement à confirmer")
                .preheader(n.fullName() + " déclare avoir payé " + money(n.amount(), n.currency()))
                .paragraph("**" + n.fullName() + "** déclare avoir réglé une échéance. Vérifiez la transaction puis confirmez-la dans le back-office.")
                .details(List.of(entry("Formation", n.bootcampTitle()), entry("Session", n.sessionName()),
                        entry("E-mail", n.to()), entry("Échéance", installment(n)),
                        entry("Montant", money(n.amount(), n.currency())), entry("Moyen", n.method()), entry("Référence", n.reference())))
                .button("Vérifier le paiement", admin("/admin/paiements"))
                .noSignature()
                .build();
        mailer.send(internalEmail, "Paiement à confirmer — " + n.fullName() + " — " + money(n.amount(), n.currency()),
                content, "INTERNAL_PAYMENT_DECLARED");
    }

    /** Aux relecteurs de la pédagogie (à défaut : l'adresse de l'équipe). */
    public void sendProgramProposalSubmitted(ProgramProposalNotice n) {
        List<String> to = n.to() == null || n.to().isEmpty() ? (isDisabled() ? List.of() : List.of(internalEmail)) : n.to();
        EmailContent content = EmailContent.builder("Proposition de programme à relire")
                .preheader(n.authorName() + " propose des modifications du programme « " + n.formationTitle() + " »")
                .paragraph("**" + n.authorName() + "** propose des modifications du programme de la formation **"
                        + n.formationTitle() + "**. Relisez-les puis validez, demandez des modifications ou refusez.")
                .button("Relire la proposition", admin(PROPOSALS_PAGE))
                .noSignature()
                .build();
        for (String recipient : to) {
            mailer.send(recipient, "Proposition de programme à relire — " + n.formationTitle(), content, "INTERNAL_PROGRAM_PROPOSAL");
        }
    }

    /** À l'auteur : la décision de la pédagogie et son commentaire. */
    public void sendProgramProposalDecision(ProgramProposalNotice n) {
        String verdict = switch (n.decision()) {
            case "APPROVED" -> "a été validée : le programme en ligne est mis à jour";
            case "CHANGES_REQUESTED" -> "vous est renvoyée : des modifications sont demandées";
            default -> "a été refusée";
        };
        String title = switch (n.decision()) {
            case "APPROVED" -> "Proposition validée";
            case "CHANGES_REQUESTED" -> "Modifications demandées";
            default -> "Proposition refusée";
        };
        EmailContent.Builder builder = EmailContent.builder(title)
                .preheader("Programme « " + n.formationTitle() + " »")
                .greeting(n.authorName() != null && !n.authorName().isBlank() ? "Bonjour " + n.authorName().trim() + "," : "Bonjour,")
                .paragraph("Votre proposition de modification du programme **" + n.formationTitle() + "** " + verdict + ".");
        if (n.comment() != null && !n.comment().isBlank()) builder.quote(n.comment().trim(), n.reviewerName());
        EmailContent content = builder.button("Voir ma proposition", admin(MY_PROPOSALS_PAGE)).build();
        for (String recipient : n.to() == null ? List.<String>of() : n.to()) {
            mailer.send(recipient, title + " — programme « " + n.formationTitle() + " »", content, "PROGRAM_PROPOSAL_DECISION");
        }
    }

    // =========================================================================
    //  CANDIDATS ET APPRENANTS
    // =========================================================================

    public void sendPaymentLinkEmail(PaymentNotice n, boolean reminder) {
        String formation = n.bootcampTitle() != null ? n.bootcampTitle() : "votre formation";
        EmailContent.Builder b = EmailContent.builder(reminder ? "Votre paiement n'a pas encore été reçu" : "Votre candidature est acceptée")
                .preheader(reminder ? "Un petit rappel pour finaliser votre inscription à " + formation
                        : "Il ne reste qu'à régler votre inscription à " + formation)
                .greetingFor(n.firstName());
        if (reminder) {
            b.paragraph("Nous n'avons pas encore reçu le paiement de votre inscription à **" + formation + "**. Voici de quoi le finaliser en quelques minutes.");
        } else {
            b.paragraph("Bonne nouvelle : votre candidature à **" + formation + "** est acceptée. Pour réserver votre place, il reste à régler votre inscription.");
        }
        EmailContent content = b
                .details(List.of(entry("Formation", n.bootcampTitle()), entry("Session", n.sessionName()),
                        entry(n.installmentCount() > 1 ? "Échéance" : "Paiement", installment(n)),
                        entry("Montant à régler", money(n.amount(), n.currency())),
                        entry("À régler avant le", n.dueDate() != null ? n.dueDate().format(DAY) : null)))
                .button("Ouvrir mon lien de paiement", n.link())
                .paragraph("Vous y trouverez le numéro Wave / Orange Money. Une fois le paiement effectué, déclarez-le sur cette même page "
                        + "(référence de la transaction) et joignez la capture d'écran : nous le vérifions rapidement.")
                .footnote("Ce lien est personnel : ne le partagez pas.")
                .build();
        mailer.send(n.to(), (reminder ? "Rappel : paiement de votre inscription" : "Paiement de votre inscription") + " — " + formation,
                content, "PAYMENT_LINK");
    }

    public void sendPaymentRejectedEmail(PaymentNotice n) {
        String formation = n.bootcampTitle() != null ? n.bootcampTitle() : "votre formation";
        EmailContent content = EmailContent.builder("Nous n'avons pas pu vérifier votre paiement")
                .preheader("Merci de le déclarer à nouveau pour " + formation)
                .greetingFor(n.firstName())
                .paragraph("Nous n'avons pas pu vérifier votre paiement de **" + money(n.amount(), n.currency()) + "** pour **" + formation + "**.")
                .quote(n.reason() != null && !n.reason().isBlank() ? n.reason() : null, "Motif indiqué par l'équipe")
                .paragraph("Merci de le déclarer à nouveau depuis votre lien de paiement (référence de la transaction et capture d'écran lisible).")
                .button("Déclarer à nouveau mon paiement", n.link())
                .note("Une question ? Répondez simplement à cet e-mail.")
                .build();
        mailer.send(n.to(), "Votre paiement n'a pas pu être vérifié", content, "PAYMENT_REJECTED");
    }

    /** Inscription confirmée pour une personne qui a déjà un compte (les nouveaux comptes reçoivent l'invitation). */
    public void sendEnrollmentConfirmedEmail(Registration r) {
        if (r.getEmail() == null || r.getEmail().isBlank()) return;
        String formation = orDash(r.getBootcampTitle());
        String start = null;
        try {
            if (r.getSession() != null && r.getSession().getStartDate() != null) start = r.getSession().getStartDate().format(DAY);
        } catch (RuntimeException e) {
            log.debug("Date de début indisponible pour l'e-mail de confirmation : {}", e.getMessage());
        }
        EmailContent content = EmailContent.builder("Votre inscription est confirmée")
                .preheader("Votre place est réservée pour " + formation)
                .greetingFor(r.getFirstName())
                .paragraph("Nous avons bien reçu votre paiement : votre place est **confirmée**. La formation est ajoutée à votre espace apprenant.")
                .details(List.of(entry("Formation", r.getBootcampTitle()), entry("Session", r.getSessionName()), entry("Début", start)))
                .button("Accéder à mon espace", admin("/espace"))
                .build();
        mailer.send(r.getEmail(), "Inscription confirmée — " + formation, content, "ENROLLMENT_CONFIRMED");
    }

    public void sendCertificateReadyEmail(CertificateNotice n) {
        EmailContent content = EmailContent.builder("Félicitations, votre certificat est prêt")
                .preheader("Certificat de réussite — " + n.formationTitle())
                .greetingFor(n.firstName())
                .paragraph("Vous avez rempli toutes les conditions de la formation **« " + n.formationTitle() + " »**. "
                        + "Votre certificat de réussite est en pièce jointe (PDF).")
                .details(List.of(entry("Formation", n.formationTitle()), entry("N° du certificat", n.publicId())))
                .button("Voir la page de vérification", n.verifyUrl())
                .secondaryButton("Ajouter à mon profil LinkedIn", n.linkedInUrl())
                .note("La page de vérification peut être partagée avec un employeur : elle confirme l'authenticité du certificat.")
                .signature("Bravo, et à très bientôt !\nL'équipe Model Technologie")
                .build();
        mailer.send(n.to(), "Votre certificat est prêt — " + n.formationTitle(), content, "CERTIFICATE_READY", null,
                List.of(new TemplatedMailer.Attachment("Certificat-" + n.publicId() + ".pdf", n.pdf(), "application/pdf")));
    }

    public void sendInvoiceEmail(InvoiceNotice n) {
        EmailContent content = EmailContent.builder("Votre facture " + n.number())
                .preheader("Facture " + n.number() + " — " + money(n.total(), n.currency()))
                .greetingFor(n.recipientName())
                .paragraph("Veuillez trouver ci-joint la facture **" + n.number() + "** (PDF).")
                .details(List.of(entry("Facture", n.number()), entry("Montant", money(n.total(), n.currency())),
                        entry("À régler avant le", n.dueDate() != null ? n.dueDate().format(DAY) : null)))
                .note("Pour toute question, répondez simplement à cet e-mail.")
                .build();
        mailer.send(n.to(), "Facture " + n.number(), content, "INVOICE", null,
                List.of(new TemplatedMailer.Attachment("Facture-" + n.number() + ".pdf", n.pdf(), "application/pdf")));
    }

    // =========================================================================
    //  COMPTES ET ABONNEMENTS
    // =========================================================================

    public void sendPasswordResetEmail(String to, String resetLink, int expiresMinutes) {
        EmailContent content = EmailContent.builder("Réinitialisez votre mot de passe")
                .preheader("Ce lien est valable " + expiresMinutes + " minutes")
                .greetingFor(null)
                .paragraph("Vous avez demandé à réinitialiser votre mot de passe. Cliquez sur le bouton ci-dessous pour en choisir un nouveau. "
                        + "Ce lien est valable **" + expiresMinutes + " minutes** et ne peut servir qu'une fois.")
                .button("Choisir un nouveau mot de passe", resetLink)
                .footnote("Si vous n'avez pas fait cette demande, ignorez simplement cet e-mail : votre mot de passe reste inchangé.")
                .build();
        mailer.send(to, "Réinitialisez votre mot de passe", content, "PASSWORD_RESET");
    }

    public void sendAccountInvitationEmail(String to, String firstName, String setupLink, int validHours, boolean learner) {
        EmailContent.Builder b = EmailContent.builder(learner ? "Bienvenue sur votre espace apprenant" : "Votre compte du back-office")
                .preheader(learner ? "Définissez votre mot de passe pour accéder à vos formations" : "Définissez votre mot de passe pour vous connecter")
                .greetingFor(firstName);
        if (learner) {
            b.paragraph("Votre inscription est confirmée : votre espace apprenant **Model Technologie** est prêt. "
                    + "Définissez votre mot de passe pour vous connecter et accéder à vos formations.");
        } else {
            b.paragraph("Un compte du back-office **Model Technologie** a été créé pour vous. Définissez votre mot de passe pour vous connecter.");
        }
        b.button("Définir mon mot de passe", setupLink)
                .note("Ce lien est valable " + validHours + " heures."
                        + (learner ? " Passé ce délai, utilisez « Mot de passe oublié » sur la page de connexion pour en recevoir un nouveau." : ""));
        mailer.send(to, learner ? "Votre accès à la plateforme de formation" : "Votre compte du back-office", b.build(), "ACCOUNT_INVITATION");
    }

    /** Prévient le titulaire que son second facteur a été retiré (perte d'appareil) : à signaler s'il n'est pas à l'origine de la demande. */
    public void sendMfaResetEmail(String to, String fullName) {
        EmailContent content = EmailContent.builder("Votre double authentification a été réinitialisée")
                .preheader("Vous devrez la configurer de nouveau à votre prochaine connexion")
                .greeting(fullName != null && !fullName.isBlank() ? "Bonjour " + fullName.trim() + "," : "Bonjour,")
                .paragraph("Un administrateur a **réinitialisé la double authentification** de votre compte du back-office. "
                        + "Toutes vos sessions ont été fermées : à votre prochaine connexion, vous configurerez l'application d'authentification sur votre appareil.")
                .button("Me connecter", admin("/connexion"))
                .footnote("Vous n'avez rien demandé ? Prévenez immédiatement un administrateur : quelqu'un a peut-être accès à votre compte.")
                .build();
        mailer.send(to, "Votre double authentification a été réinitialisée", content, "MFA_RESET");
    }

    public void sendNewsletterConfirmationEmail(String to, String confirmLink, int validDays) {
        EmailContent content = EmailContent.builder("Confirmez votre inscription")
                .preheader("Un dernier clic pour recevoir la newsletter")
                .greetingFor(null)
                .paragraph("Merci de votre intérêt pour la newsletter de **Model Technologie**. "
                        + "Confirmez votre adresse pour commencer à la recevoir.")
                .button("Confirmer mon inscription", confirmLink)
                .note("Ce lien est valable " + validDays + " jours.")
                .footnote("Si vous n'êtes pas à l'origine de cette demande, ignorez simplement cet e-mail : vous ne recevrez rien.")
                .build();
        mailer.send(to, "Confirmez votre inscription à la newsletter", content, "NEWSLETTER_CONFIRMATION");
    }

    // =========================================================================
    //  OUTILS
    // =========================================================================

    private String admin(String path) {
        return frontendUrl.replaceAll("/+$", "") + path;
    }

    private String installment(PaymentNotice n) {
        return n.installmentCount() > 1 ? n.installmentNumber() + " sur " + n.installmentCount() : "Paiement unique";
    }

    /** 150000 + XOF → « 150 000 FCFA » (séparateur de milliers français). */
    static String money(long amount, String currency) {
        String formatted = String.format(Locale.FRANCE, "%,d", amount).replace(' ', ' ');
        return formatted + " " + ("XOF".equals(currency) || currency == null ? "FCFA" : currency);
    }

    private String fullName(Registration r) {
        return ((r.getFirstName() == null ? "" : r.getFirstName()) + " " + (r.getLastName() == null ? "" : r.getLastName())).trim();
    }

    private String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private boolean isDisabled() {
        if (internalEmail == null || internalEmail.isBlank()) {
            log.debug("Email destinataire interne non configuré, notification ignorée");
            return true;
        }
        return false;
    }
}
