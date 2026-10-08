package com.modeltech.datamasteryhub.modules.notification.service.impl;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.notification.service.EmailNotifier;
import com.modeltech.datamasteryhub.modules.notification.service.CertificateNotice;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.notification.service.PaymentNotice;
import com.modeltech.datamasteryhub.modules.notification.service.SlackNotifier;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final SlackNotifier  slackNotifier;
    private final EmailNotifier  emailNotifier;

    // =========================================================================
    //  INSCRIPTIONS BOOTCAMP
    // =========================================================================

    /**
     * Notification interne (Slack) à l'équipe + email interne récap.
     * Déjà existant — inchangé.
     */
    @Override
    @Async
    public void notifyNewRegistration(Registration registration) {
        log.info("Notifications internes pour l'inscription de {} {}",
                registration.getFirstName(), registration.getLastName());

        try {
            slackNotifier.send(registration);
        } catch (Exception e) {
            log.error("Erreur Slack : {}", e.getMessage());
        }

        try {
            emailNotifier.send(registration);           // email interne vers l'équipe
        } catch (Exception e) {
            log.error("Erreur Email interne : {}", e.getMessage());
        }
    }

    /**
     * Email vers le CANDIDAT : inscription reçue + instructions de paiement.
     * Déclenché juste après la sauvegarde (statut PENDING).
     */
    @Override
    @Async
    public void sendRegistrationPendingEmail(Registration registration) {
        log.info("Email 'inscription en attente' → {} ({})",
                registration.getFirstName(), registration.getEmail());
        try {
            emailNotifier.sendPendingConfirmationToCandidate(registration);
        } catch (Exception e) {
            log.error("Erreur Email 'pending' candidat {} : {}", registration.getEmail(), e.getMessage());
        }
    }

    /**
     * Email vers le CANDIDAT : place définitivement confirmée.
     * Déclenché quand le backoffice passe le statut PENDING → CONFIRMED.
     */
    @Override
    @Async
    public void sendRegistrationConfirmedEmail(Registration registration) {
        log.info("Email 'inscription confirmée' → {} ({})",
                registration.getFirstName(), registration.getEmail());
        try {
            emailNotifier.sendConfirmedToCandidate(registration);
        } catch (Exception e) {
            log.error("Erreur Email 'confirmed' candidat {} : {}", registration.getEmail(), e.getMessage());
        }
    }

    // =========================================================================
    //  PAIEMENTS
    // =========================================================================

    @Override
    @Async
    public void sendPaymentLinkEmail(PaymentNotice notice, boolean reminder) {
        try {
            emailNotifier.sendPaymentLinkEmail(notice, reminder);
        } catch (Exception e) {
            log.error("Erreur email lien de paiement {} : {}", notice.to(), e.getMessage());
        }
    }

    @Override
    @Async
    public void sendPaymentRejectedEmail(PaymentNotice notice) {
        try {
            emailNotifier.sendPaymentRejectedEmail(notice);
        } catch (Exception e) {
            log.error("Erreur email paiement refusé {} : {}", notice.to(), e.getMessage());
        }
    }

    @Override
    @Async
    public void notifyPaymentDeclared(PaymentNotice notice) {
        try {
            slackNotifier.sendPaymentDeclared(notice);
        } catch (Exception e) {
            log.error("Erreur Slack (paiement déclaré) : {}", e.getMessage());
        }
        try {
            emailNotifier.sendPaymentDeclaredInternal(notice);
        } catch (Exception e) {
            log.error("Erreur email interne (paiement déclaré) : {}", e.getMessage());
        }
    }

    // =========================================================================
    //  CERTIFICATS
    // =========================================================================

    @Override
    @Async
    public void sendCertificateReadyEmail(CertificateNotice notice) {
        try {
            emailNotifier.sendCertificateReadyEmail(notice);
        } catch (Exception e) {
            log.error("Erreur email certificat {} : {}", notice.to(), e.getMessage());
        }
    }

    // =========================================================================
    //  CONTACT
    // =========================================================================

    @Override
    @Async
    public void notifyNewContactMessage(ContactMessage contactMessage) {
        log.info("Notifications pour le message de contact de {} {}",
                contactMessage.getFirstName(), contactMessage.getLastName());
        slackNotifier.sendContactMessage(contactMessage);
        emailNotifier.sendContactMessage(contactMessage);
    }

    // =========================================================================
    //  NEWSLETTER
    // =========================================================================

    @Override
    @Async
    public void sendNewsletterConfirmationEmail(String to, String confirmLink, int validDays) {
        log.info("Email de confirmation newsletter → {}", to);
        emailNotifier.sendNewsletterConfirmationEmail(to, confirmLink, validDays);
    }

    // =========================================================================
    //  AUTH
    // =========================================================================

    @Override
    public void notifyPasswordResetEmail(String to, String resetLink, int expiresMinutes) {
        log.info("Email de réinitialisation de mot de passe → {}", to);
        emailNotifier.sendPasswordResetEmail(to, resetLink, expiresMinutes);
    }

    @Override
    @Async
    public void sendAccountInvitationEmail(String to, String firstName, String setupLink, int validHours, boolean learner) {
        log.info("Email d'invitation ({}) → {}", learner ? "apprenant" : "back-office", to);
        emailNotifier.sendAccountInvitationEmail(to, firstName, setupLink, validHours, learner);
    }
}
