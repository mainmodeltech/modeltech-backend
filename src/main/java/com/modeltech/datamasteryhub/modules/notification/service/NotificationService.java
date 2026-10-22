package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;

public interface NotificationService {

    // ── Inscriptions bootcamp ──────────────────────────────────────────────

    /** Notifie l'équipe (Slack) d'une nouvelle inscription. */
    void notifyNewRegistration(Registration registration);

    /**
     * Inscription confirmée par le paiement, pour une personne qui a déjà un compte actif (les nouveaux
     * comptes reçoivent l'invitation à définir leur mot de passe, qui vaut confirmation).
     */
    void sendEnrollmentConfirmedEmail(Registration registration);

    // ── Paiements ──────────────────────────────────────────────────────────

    /** Lien de paiement envoyé au candidat (acceptation, échéance ou relance). */
    void sendPaymentLinkEmail(PaymentNotice notice, boolean reminder);

    /** Le paiement déclaré n'a pas pu être vérifié : le candidat doit le refaire. */
    void sendPaymentRejectedEmail(PaymentNotice notice);

    /** Notifie l'équipe (Slack + e-mail interne) qu'un paiement attend sa vérification. */
    void notifyPaymentDeclared(PaymentNotice notice);

    // ── Programme ──────────────────────────────────────────────────────────

    /** Une proposition de modification du programme attend la relecture de la pédagogie. */
    void notifyProgramProposalSubmitted(ProgramProposalNotice notice);

    /** Décision de la pédagogie sur sa proposition (validée, modifications demandées, refusée), envoyée à l'auteur. */
    void sendProgramProposalDecision(ProgramProposalNotice notice);

    // ── Certificats ────────────────────────────────────────────────────────

    /** « Votre certificat est prêt » : lien de vérification, lien LinkedIn et PDF en pièce jointe. */
    void sendCertificateReadyEmail(CertificateNotice notice);

    // ── Factures ───────────────────────────────────────────────────────────

    /** Facture PDF en pièce jointe. */
    void sendInvoiceEmail(InvoiceNotice notice);

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
    /** Prévient un compte du personnel que sa double authentification a été réinitialisée par un administrateur. */
    void sendMfaResetEmail(String to, String fullName);

    void sendAccountInvitationEmail(String to, String firstName, String setupLink, int validHours, boolean learner);
}
