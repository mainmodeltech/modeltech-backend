package com.modeltech.datamasteryhub.modules.notification.channel;

import com.modeltech.datamasteryhub.modules.notification.service.ResilientMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/** Canal e-mail : texte brut, avec reprises, journal et redirection de recette (voir {@link ResilientMailSender}). */
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    public static final String NAME = "EMAIL";

    private final ResilientMailSender mailSender;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean canReach(OutboundMessage message) {
        return message.toEmail() != null && !message.toEmail().isBlank();
    }

    @Override
    public boolean send(OutboundMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(mailSender.fromAddress());
        mail.setTo(message.toEmail());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        if (message.replyTo() != null && !message.replyTo().isBlank()) mail.setReplyTo(message.replyTo());
        return mailSender.send(mail, message.type());
    }
}
