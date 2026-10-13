package com.modeltech.datamasteryhub.modules.notification.channel;

import com.modeltech.datamasteryhub.modules.notification.service.TemplatedMailer;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canal e-mail : mise en page de la charte (HTML + texte), avec reprises, journal et redirection de
 * recette (voir {@link TemplatedMailer}). Sans mise en page fournie, le texte du message est repris tel quel.
 */
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    public static final String NAME = "EMAIL";

    private final TemplatedMailer mailer;

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
        EmailContent content = message.email() != null ? message.email() : fromPlainText(message);
        return mailer.send(message.toEmail(), message.subject(), content, message.type(), message.replyTo(), List.of());
    }

    /** Message texte : un paragraphe par bloc séparé d'une ligne vide ; le texte contient déjà sa formule de fin. */
    static EmailContent fromPlainText(OutboundMessage message) {
        EmailContent.Builder b = EmailContent.builder(message.subject()).noSignature();
        String body = message.body() == null ? "" : message.body().strip();
        for (String paragraph : body.split("\\R\\s*\\R")) b.paragraph(paragraph.strip());
        return b.build();
    }
}
