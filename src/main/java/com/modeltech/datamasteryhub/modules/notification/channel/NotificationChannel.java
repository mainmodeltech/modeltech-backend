package com.modeltech.datamasteryhub.modules.notification.channel;

/**
 * Un canal de diffusion. Ajouter WhatsApp = écrire un {@code @Component} qui implémente cette interface
 * et l'activer dans {@code app.messaging.channels} : aucun appelant ne change.
 */
public interface NotificationChannel {

    /** Nom stable du canal, tel qu'écrit dans {@code app.messaging.channels} (ex. {@code EMAIL}). */
    String name();

    /** Le canal peut-il joindre ce destinataire ? (ex. WhatsApp exige un numéro) */
    boolean canReach(OutboundMessage message);

    /** @return true si le message est parti ; ne lève jamais d'exception */
    boolean send(OutboundMessage message);
}
