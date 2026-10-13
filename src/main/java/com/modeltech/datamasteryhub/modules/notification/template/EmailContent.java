package com.modeltech.datamasteryhub.modules.notification.template;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenu d'un e-mail, indépendant de sa mise en page : un titre, des blocs (paragraphes, tableau de
 * détails, bouton, code, citation, note) et une signature. {@link EmailRenderer} en tire une version HTML
 * (gabarit de la marque) et une version texte brut ; tous les e-mails de la plateforme passent par ce modèle.
 *
 * <p>Dans les textes : {@code **gras**}, retours à la ligne conservés, adresses {@code https://…} rendues
 * cliquables. Tout le reste est échappé (jamais de HTML brut venant d'un utilisateur).
 */
public final class EmailContent {

    public sealed interface Block permits Paragraph, Details, Button, Code, Quote, Note {}

    public record Paragraph(String text) implements Block {}

    public record Entry(String label, String value) {}

    /** Tableau « libellé : valeur » (récapitulatif d'une formation, d'un paiement…). */
    public record Details(List<Entry> entries) implements Block {}

    /** Bouton d'action ; {@code primary=false} donne un bouton discret (contour). */
    public record Button(String label, String url, boolean primary) implements Block {}

    /** Valeur à recopier telle quelle (code de connexion). */
    public record Code(String label, String value) implements Block {}

    /** Message cité (question d'un apprenant, texte d'un motif de refus). */
    public record Quote(String text, String author) implements Block {}

    /** Précision en petit, grisée. */
    public record Note(String text) implements Block {}

    private final String title;
    private final String preheader;
    private final String greeting;
    private final List<Block> blocks;
    private final String signature;
    private final String footnote;

    private EmailContent(Builder b) {
        this.title = b.title;
        this.preheader = b.preheader;
        this.greeting = b.greeting;
        this.blocks = List.copyOf(b.blocks);
        this.signature = b.signature;
        this.footnote = b.footnote;
    }

    public String title() { return title; }
    /** Résumé affiché dans la liste de la boîte de réception, avant l'ouverture du message. */
    public String preheader() { return preheader; }
    public String greeting() { return greeting; }
    public List<Block> blocks() { return blocks; }
    /** Formule de fin (vide = pas de signature, ex. message d'un formateur qui signe lui-même). */
    public String signature() { return signature; }
    /** Mention en bas de carte (« Si vous n'êtes pas à l'origine de cette demande… »). */
    public String footnote() { return footnote; }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static final class Builder {
        private final String title;
        private String preheader = "";
        private String greeting;
        private final List<Block> blocks = new ArrayList<>();
        private String signature = "À bientôt,\nL'équipe Model Technologie";
        private String footnote;

        private Builder(String title) {
            this.title = title == null ? "" : title;
        }

        public Builder preheader(String text) { this.preheader = text == null ? "" : text; return this; }

        /** « Bonjour Awa, » ; prénom vide → « Bonjour, ». */
        public Builder greetingFor(String firstName) {
            this.greeting = firstName != null && !firstName.isBlank() ? "Bonjour " + firstName.trim() + "," : "Bonjour,";
            return this;
        }

        public Builder greeting(String text) { this.greeting = text; return this; }

        public Builder paragraph(String text) {
            if (text != null && !text.isBlank()) blocks.add(new Paragraph(text));
            return this;
        }

        public Builder details(List<Entry> entries) {
            List<Entry> kept = entries.stream().filter(e -> e.value() != null && !e.value().isBlank()).toList();
            if (!kept.isEmpty()) blocks.add(new Details(kept));
            return this;
        }

        public Builder button(String label, String url) {
            if (url != null && !url.isBlank()) blocks.add(new Button(label, url, true));
            return this;
        }

        public Builder secondaryButton(String label, String url) {
            if (url != null && !url.isBlank()) blocks.add(new Button(label, url, false));
            return this;
        }

        public Builder code(String label, String value) {
            blocks.add(new Code(label, value));
            return this;
        }

        public Builder quote(String text, String author) {
            if (text != null && !text.isBlank()) blocks.add(new Quote(text, author));
            return this;
        }

        public Builder note(String text) {
            if (text != null && !text.isBlank()) blocks.add(new Note(text));
            return this;
        }

        public Builder signature(String text) { this.signature = text; return this; }

        public Builder noSignature() { this.signature = ""; return this; }

        public Builder footnote(String text) { this.footnote = text; return this; }

        public EmailContent build() {
            return new EmailContent(this);
        }
    }

    /** Raccourci : {@code Entry}. */
    public static Entry entry(String label, String value) {
        return new Entry(label, value);
    }
}
