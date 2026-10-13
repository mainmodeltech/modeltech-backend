package com.modeltech.datamasteryhub.modules.notification.template;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import java.util.ArrayList;
import java.util.List;

/** Outil de test : lit les parties d'un message MIME (texte, HTML, pièces jointes). */
public final class MailParts {

    public String text;
    public String html;
    public final List<String> attachments = new ArrayList<>();

    public static MailParts of(MimeMessage message) throws Exception {
        MailParts parts = new MailParts();
        message.saveChanges();   // calcule les en-têtes de contenu des parties imbriquées
        parts.walk(message);
        return parts;
    }

    private void walk(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                walk(child);
            }
            return;
        }
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            attachments.add(part.getFileName());
        } else if (part.isMimeType("text/plain") && text == null) {
            text = (String) part.getContent();
        } else if (part.isMimeType("text/html") && html == null) {
            html = (String) part.getContent();
        }
    }
}
