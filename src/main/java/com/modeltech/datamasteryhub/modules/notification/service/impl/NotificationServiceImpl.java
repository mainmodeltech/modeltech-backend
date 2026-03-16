package com.modeltech.datamasteryhub.modules.notification.service.impl;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.notification.service.EmailNotifier;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
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
    //  AUTH
    // =========================================================================

    @Override
    public void notifyPasswordResetEmail(String to, String resetLink, int expiresMinutes) {
        log.info("Email de réinitialisation de mot de passe → {}", to);
        emailNotifier.sendPasswordResetEmail(to, resetLink, expiresMinutes);
    }
}
