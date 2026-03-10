package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;

public interface NotificationService {

    /**
     * Envoie les notifications (Slack + Email) pour une nouvelle inscription.
     * Appel asynchrone pour ne pas bloquer la reponse API.
     */
    void notifyNewRegistration(Registration registration);

    /**
     * Envoie les notifications (Slack + Email) pour un nouveau message de contact.
     * Appel asynchrone pour ne pas bloquer la réponse API.
     */
    void notifyNewContactMessage(ContactMessage contactMessage);

    /**
     * Envoie un email de réinitialisation de mot de passe avec un lien sécurisé.
     * @param to
     * @param resetLink
     * @param expiresMinutes
     */
    void notifyPasswordResetEmail(String to, String resetLink, int expiresMinutes);
}
