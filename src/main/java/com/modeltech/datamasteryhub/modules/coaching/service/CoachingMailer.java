package com.modeltech.datamasteryhub.modules.coaching.service;

import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingRequest;
import com.modeltech.datamasteryhub.modules.notification.service.TemplatedMailer;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * E-mails du parcours de coaching. Un e-mail qui ne part pas ne remet jamais en cause une étape déjà enregistrée : {@link TemplatedMailer}
 * ne lève pas d'exception, et chaque envoi est tracé dans le journal d'e-mails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoachingMailer {

    private final TemplatedMailer mailer;

    @Value("${app.frontend.url:http://localhost:8088}")
    private String frontendUrl;

    /** Destinataires internes (équipe pédagogique et finance), séparés par des virgules. */
    @Value("${app.coaching.notify-to:}")
    private String notifyTo;

    @Value("${app.notifications.email.to:admin@model-technologie.com}")
    private String defaultNotifyTo;

    /** Lien de réservation d'un échange de présentation (15 à 20 minutes) ; vide = non proposé. */
    @Value("${app.coaching.booking-url:}")
    private String bookingUrl;

    public String bookingUrl() {
        return bookingUrl == null || bookingUrl.isBlank() ? null : bookingUrl.trim();
    }

    public String paymentLink(CoachingRequest r) {
        return frontendUrl + "/coaching/paiement/" + r.getPayToken();
    }

    private static String amount(long xof) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(xof).replace(' ', ' ').replace(' ', ' ') + " FCFA";
    }

    private static String greeting(CoachingRequest r) {
        return "Bonjour " + r.getFirstName().trim() + ",";
    }

    private void toStaff(String subject, EmailContent content, String type) {
        String recipients = notifyTo == null || notifyTo.isBlank() ? defaultNotifyTo : notifyTo;
        for (String to : recipients.split(",")) {
            if (!to.isBlank()) mailer.send(to.trim(), subject, content, type);
        }
    }

    /** Accusé de réception au demandeur, avec le lien de réservation de l'échange de présentation s'il est configuré. */
    public void requestReceived(CoachingRequest r) {
        EmailContent.Builder b = EmailContent.builder("Votre demande de coaching est bien reçue")
                .preheader("Nous revenons vers vous très vite")
                .greeting(greeting(r))
                .paragraph("Nous avons bien reçu votre demande de **coaching**. Notre responsable pédagogique l'étudie, échange avec le formateur le plus adapté, "
                        + "puis revient vers vous avec une proposition (contenu et tarif) et un lien de paiement sécurisé.");
        if (bookingUrl() != null) {
            b.paragraph("Pour gagner du temps, vous pouvez dès maintenant réserver un **échange de présentation de 15 à 20 minutes** : il nous permet de bien cerner votre besoin.")
                    .button("Réserver un échange", bookingUrl());
        }
        mailer.send(r.getEmail(), "Votre demande de coaching est bien reçue",
                b.footnote("Aucun paiement n'est demandé à ce stade. Une question ? Répondez simplement à ce message.").build(), "COACHING_RECEIVED");
    }

    /** Alerte de l'équipe : nouvelle demande à traiter. */
    public void staffNewRequest(CoachingRequest r) {
        List<EmailContent.Entry> rows = new ArrayList<>(List.of(
                new EmailContent.Entry("Demandeur", r.fullName() + (r.getCompany() != null && !r.getCompany().isBlank() ? " (" + r.getCompany() + ")" : "")),
                new EmailContent.Entry("E-mail", r.getEmail())));
        if (r.getPhone() != null && !r.getPhone().isBlank()) rows.add(new EmailContent.Entry("Téléphone", r.getPhone()));
        if (r.getTier() != null && !r.getTier().isBlank()) rows.add(new EmailContent.Entry("Formule", r.getTier()));
        if (r.getSlot() != null && !r.getSlot().isBlank()) rows.add(new EmailContent.Entry("Créneau souhaité", r.getSlot()));
        EmailContent.Builder b = EmailContent.builder("Nouvelle demande de coaching")
                .preheader(r.fullName())
                .paragraph("Une nouvelle demande de coaching attend d'être analysée.")
                .details(rows);
        if (r.getMessage() != null && !r.getMessage().isBlank()) b.paragraph("Message : " + r.getMessage().trim());
        toStaff("Nouvelle demande de coaching : " + r.fullName(),
                b.button("Traiter la demande", frontendUrl + "/admin/coaching").build(), "COACHING_STAFF_NEW");
    }

    /** Lien de paiement envoyé au demandeur une fois la demande validée. */
    public void paymentLink(CoachingRequest r, boolean reminder) {
        String subject = reminder ? "Rappel : votre coaching, lien de paiement" : "Votre coaching est validé : lien de paiement";
        EmailContent.Builder b = EmailContent.builder(subject)
                .preheader(r.getOfferLabel() + " : " + amount(r.getAmountXof()))
                .greeting(greeting(r))
                .paragraph("Bonne nouvelle : votre demande de coaching est **validée**. Voici ce qui a été convenu.")
                .details(List.of(
                        new EmailContent.Entry("Coaching", r.getOfferLabel()),
                        new EmailContent.Entry("Formateur", r.getTrainerName()),
                        new EmailContent.Entry("Montant", amount(r.getAmountXof())),
                        new EmailContent.Entry("Lien valable jusqu'au", r.getTokenExpiresAt().format(DateTimeFormatter.ofPattern("d/MM/yyyy")))))
                .paragraph("Le paiement finalise votre réservation. Vous pouvez payer en ligne (Wave, Orange Money, carte) ou, si vous préférez, par virement ou mobile money.")
                .button("Payer mon coaching", paymentLink(r));
        mailer.send(r.getEmail(), subject, b.footnote("Ce lien est personnel : ne le partagez pas. Une question ? Répondez à ce message.").build(), "COACHING_PAYMENT_LINK");
    }

    /** Refus de la demande, avec le motif donné par l'équipe. */
    public void declined(CoachingRequest r) {
        mailer.send(r.getEmail(), "Votre demande de coaching",
                EmailContent.builder("À propos de votre demande de coaching")
                        .greeting(greeting(r))
                        .paragraph("Après étude de votre demande, nous ne sommes malheureusement pas en mesure d'y donner suite pour le moment.")
                        .paragraph("Motif : " + r.getClosingReason())
                        .paragraph("N'hésitez pas à nous réécrire si votre besoin évolue.")
                        .footnote("Vous pouvez répondre directement à ce message.").build(), "COACHING_DECLINED");
    }

    /** Paiement confirmé : confirmation au client, alerte à l'équipe et au formateur pour planifier. */
    public void paid(CoachingRequest r) {
        List<EmailContent.Entry> rows = List.of(
                new EmailContent.Entry("Coaching", r.getOfferLabel()),
                new EmailContent.Entry("Formateur", r.getTrainerName()),
                new EmailContent.Entry("Montant réglé", amount(r.getPaidAmountXof())));
        EmailContent.Builder b = EmailContent.builder("Votre coaching est confirmé")
                .preheader("Paiement reçu : merci")
                .greeting(greeting(r))
                .paragraph("Merci : votre paiement est **confirmé** et votre coaching est réservé. Votre formateur vous contacte très bientôt pour planifier les séances.")
                .details(rows);
        if (r.getReceiptUrl() != null && r.getReceiptUrl().startsWith("https://")) b.button("Voir mon reçu de paiement", r.getReceiptUrl());
        mailer.send(r.getEmail(), "Votre coaching est confirmé", b.footnote("Une question ? Répondez à ce message.").build(), "COACHING_PAID");

        EmailContent staff = EmailContent.builder("Coaching payé : à planifier")
                .paragraph("Le paiement de ce coaching est confirmé : le formateur doit prendre contact avec le client pour planifier les séances.")
                .details(List.of(
                        new EmailContent.Entry("Client", r.fullName() + " <" + r.getEmail() + ">"),
                        new EmailContent.Entry("Téléphone", r.getPhone() == null ? "—" : r.getPhone()),
                        rows.get(0), rows.get(1), rows.get(2)))
                .build();
        List<String> to = new ArrayList<>();
        if (r.getTrainerEmail() != null && !r.getTrainerEmail().isBlank()) to.add(r.getTrainerEmail().trim());
        if (r.getAssignedTo() != null && !r.getAssignedTo().isBlank()) to.add(r.getAssignedTo().trim());
        if (to.isEmpty()) toStaff("Coaching payé : " + r.fullName(), staff, "COACHING_STAFF_PAID");
        else to.stream().distinct().forEach(t -> mailer.send(t, "Coaching payé : " + r.fullName(), staff, "COACHING_STAFF_PAID"));
    }

    /** Paiement manuel déclaré par le client : la Finance doit vérifier. */
    public void staffPaymentDeclared(CoachingRequest r) {
        toStaff("Paiement de coaching à vérifier : " + r.fullName(),
                EmailContent.builder("Paiement de coaching à vérifier")
                        .paragraph("Le client indique avoir payé son coaching. Merci de vérifier la réception du paiement, puis de le confirmer ou de le refuser.")
                        .details(List.of(
                                new EmailContent.Entry("Client", r.fullName()),
                                new EmailContent.Entry("Montant attendu", amount(r.getAmountXof())),
                                new EmailContent.Entry("Moyen", String.valueOf(r.getManualMethod())),
                                new EmailContent.Entry("Référence", r.getManualReference())))
                        .button("Vérifier le paiement", frontendUrl + "/admin/coaching").build(), "COACHING_STAFF_DECLARED");
    }
}
