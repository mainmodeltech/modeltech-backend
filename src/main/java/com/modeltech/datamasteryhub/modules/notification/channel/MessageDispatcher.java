package com.modeltech.datamasteryhub.modules.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Envoie un message sur les canaux activés ({@code app.messaging.channels}, {@code EMAIL} par défaut) qui savent
 * joindre le destinataire. Un message est « livré » dès qu'au moins un canal l'a envoyé.
 */
@Component
@Slf4j
public class MessageDispatcher {

    private final List<NotificationChannel> channels;
    private final Set<String> enabled;

    public MessageDispatcher(List<NotificationChannel> channels,
                             @Value("${app.messaging.channels:EMAIL}") String enabledChannels) {
        this.channels = channels;
        this.enabled = Arrays.stream(enabledChannels.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /** Envoi synchrone (planificateur, tests). */
    public boolean send(OutboundMessage message) {
        boolean delivered = false;
        for (NotificationChannel channel : channels) {
            if (!enabled.contains(channel.name()) || !channel.canReach(message)) continue;
            try {
                delivered |= channel.send(message);
            } catch (RuntimeException e) {
                log.error("Canal {} : échec pour « {} » : {}", channel.name(), message.type(), e.getMessage());
            }
        }
        return delivered;
    }

    /** Envoi d'une liste en tâche de fond (un seul travail : la requête HTTP n'attend pas les e-mails). */
    @Async
    public void sendAll(List<OutboundMessage> messages, IntConsumer onDone) {
        int delivered = 0;
        for (OutboundMessage m : messages) {
            if (send(m)) delivered++;
        }
        log.info("{} message(s) sur {} livré(s)", delivered, messages.size());
        if (onDone != null) onDone.accept(delivered);
    }
}
