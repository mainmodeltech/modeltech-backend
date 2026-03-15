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

    // ── Contact ────────────────────────────────────────────────────────────

    void notifyNewContactMessage(ContactMessage contactMessage);

    // ── Auth ───────────────────────────────────────────────────────────────

    void notifyPasswordResetEmail(String to, String resetLink, int expiresMinutes);
}
