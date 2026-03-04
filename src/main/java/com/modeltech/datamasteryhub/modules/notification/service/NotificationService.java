package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.training.entity.Registration;

public interface NotificationService {

    /**
     * Envoie les notifications (Slack + Email) pour une nouvelle inscription.
     * Appel asynchrone pour ne pas bloquer la reponse API.
     */
    void notifyNewRegistration(Registration registration);
}
