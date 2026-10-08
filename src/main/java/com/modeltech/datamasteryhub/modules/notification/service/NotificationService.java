package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;

public interface NotificationService {

    // ── Inscriptions bootcamp ──────────────────────────────────────────────

    /** Notifie l'équipe (Slack) d'une nouvelle inscription. */
    void notifyNewRegistration(Registration registration);

    /**
     * Envoie un email au CANDIDAT pour confirmer la réception de son inscription
     * et lui communiquer les instructions de paiement.
     * Déclenché immédiatement après la sauvegarde (statut PENDING).
     */
    void sendRegistrationPendingEmail(Registration registration);

    /**
     * Envoie un email au CANDIDAT pour lui confirmer que sa place est
     * définitivement réservée suite à la validation de son paiement.
     * Déclenché quand le backoffice passe le statut à CONFIRMED.
     */
    void sendRegistrationConfirmedEmail(Registration registration);

    // ── Paiements ──────────────────────────────────────────────────────────

    /** Lien de paiement envoyé au candidat (acceptation, échéance ou relance). */
    void sendPaymentLinkEmail(PaymentNotice notice, boolean reminder);

    /** Le paiement déclaré n'a pas pu être vérifié : le candidat doit le refaire. */
    void sendPaymentRejectedEmail(PaymentNotice notice);

    /** Notifie l'équipe (Slack + e-mail interne) qu'un paiement attend sa vérification. */
    void notifyPaymentDeclared(PaymentNotice notice);

    // ── Certificats ────────────────────────────────────────────────────────

    /** « Votre certificat est prêt » : lien de vérification, lien LinkedIn et PDF en pièce jointe. */
    void sendCertificateReadyEmail(CertificateNotice notice);

    // ── Contact ────────────────────────────────────────────────────────────

    void notifyNewContactMessage(ContactMessage contactMessage);

    // ── Newsletter ─────────────────────────────────────────────────────────

    /** E-mail de confirmation (double opt-in) envoyé à l'abonné. */
    void sendNewsletterConfirmationEmail(String to, String confirmLink, int validDays);

    // ── Auth ───────────────────────────────────────────────────────────────

    void notifyPasswordResetEmail(String to, String resetLink, int expiresMinutes);

    /**
     * Invitation à définir son mot de passe (compte apprenant ou back-office créé par un admin).
     *
     * @param learner true pour un apprenant, false pour un compte de back-office
     */
    void sendAccountInvitationEmail(String to, String firstName, String setupLink, int validHours, boolean learner);
}
