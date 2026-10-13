package com.modeltech.datamasteryhub.modules.notification.template;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.modeltech.datamasteryhub.modules.notification.template.EmailContent.entry;
import static org.assertj.core.api.Assertions.assertThat;

/** La charte d'e-mail : structure, échappement, version texte. */
class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer(new EmailRenderer.Brand(
            "Model Technologie", "https://www.model-technologie.com", "https://www.model-technologie.com/logo.png",
            "129 Liberté 6 extension, Dakar", "+221 78 862 52 38", "contact@model-technologie.com", "https://www.model-technologie.com"));

    private EmailContent sample() {
        return EmailContent.builder("Votre candidature est acceptée")
                .preheader("Il ne reste qu'à régler")
                .greetingFor("Awa")
                .paragraph("Votre place pour **Power BI** est réservée.")
                .details(List.of(entry("Formation", "Power BI"), entry("Montant", "150 000 FCFA"), entry("Session", "")))
                .button("Ouvrir mon lien de paiement", "https://www.model-technologie.com/paiement/abc?x=1&y=2")
                .secondaryButton("Voir le certificat", "https://www.model-technologie.com/certificats/MT-1")
                .code("Code", "123456")
                .quote("Merci de me rappeler", "Awa Diop")
                .note("Valable 24 heures.")
                .footnote("Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.")
                .build();
    }

    @Test
    void producesACompleteHtmlDocument_withTheBrandAndEveryBlock() {
        String html = renderer.render(sample()).html();

        assertThat(html).startsWith("<!DOCTYPE html>").contains("<html lang=\"fr\"");
        assertThat(html).contains("<title>Votre candidature est acceptée</title>");
        assertThat(html).contains("Il ne reste qu&#39;à régler");                               // aperçu de la boîte de réception
        assertThat(html).contains("Bonjour Awa,");
        assertThat(html).contains("<strong>Power BI</strong>");
        assertThat(html).contains("Formation").contains("150 000 FCFA");
        assertThat(html).contains("Ouvrir mon lien de paiement").contains("Voir le certificat");
        assertThat(html).contains("123456").contains("Merci de me rappeler").contains("Awa Diop");
        assertThat(html).contains("Valable 24 heures.").contains("ignorez ce message");
        assertThat(html).contains("À bientôt,<br>L&#39;équipe Model Technologie");
        assertThat(html).contains("129 Liberté 6 extension, Dakar").contains("+221 78 862 52 38");
        assertThat(html).contains("https://www.model-technologie.com/logo.png");
        assertThat(html).doesNotContain("{{");                                                  // aucun champ non remplacé
    }

    @Test
    void ignoresEmptyDetailRows_andLinksTheButtonWithAFallbackUrl() {
        String html = renderer.render(sample()).html();

        assertThat(html).doesNotContain(">Session<");                                           // valeur vide : ligne absente
        assertThat(html).contains("href=\"https://www.model-technologie.com/paiement/abc?x=1&amp;y=2\"");
        assertThat(html).contains("Si le bouton ne fonctionne pas");                           // lien de secours sous le bouton principal
    }

    @Test
    void escapesEverythingTypedByAUser() {
        EmailContent hostile = EmailContent.builder("<script>alert(1)</script>")
                .greeting("Bonjour <b>x</b>,")
                .paragraph("<img src=x onerror=alert(1)> \"guillemets\" & **<i>gras</i>**")
                .details(List.of(entry("<u>Libellé</u>", "<iframe src=//evil></iframe>")))
                .button("<b>Cliquer</b>", "https://x.test/a\"onmouseover=\"alert(1)")
                .quote("</div><script>1</script>", "<b>Auteur</b>")
                .build();

        String html = renderer.render(hostile).html();

        assertThat(html).doesNotContain("<script>alert").doesNotContain("<img src=x").doesNotContain("<iframe")
                .doesNotContain("<b>x</b>").doesNotContain("<b>Cliquer</b>").doesNotContain("<u>Libellé</u>")
                .doesNotContain("</div><script>");
        assertThat(html).doesNotContain("href=\"https://x.test/a\"onmouseover=");              // le guillemet ne referme pas l'attribut
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).contains("<strong>&lt;i&gt;gras&lt;/i&gt;</strong>");
    }

    @Test
    void makesBareUrlsClickable_andKeepsLineBreaks() {
        String html = renderer.render(EmailContent.builder("Message")
                .paragraph("Rejoignez-nous sur https://meet.example.com/abc-def, merci.\nÀ tout à l'heure").build()).html();

        assertThat(html).contains("<a href=\"https://meet.example.com/abc-def\"");
        assertThat(html).contains("</a>, merci.<br>À tout à l&#39;heure");                       // virgule finale hors du lien
    }

    @Test
    void textVersionCarriesTheSameInformationWithoutMarkup() {
        String text = renderer.render(sample()).text();

        assertThat(text).startsWith("Votre candidature est acceptée");
        assertThat(text).contains("Bonjour Awa,").contains("Votre place pour Power BI est réservée.");
        assertThat(text).contains("Formation : Power BI").contains("Montant : 150 000 FCFA").doesNotContain("Session :");
        assertThat(text).contains("Ouvrir mon lien de paiement :\nhttps://www.model-technologie.com/paiement/abc?x=1&y=2");
        assertThat(text).contains("Code : 123456").contains("> Merci de me rappeler").contains("— Awa Diop");
        assertThat(text).contains("À bientôt,\nL'équipe Model Technologie");
        assertThat(text).contains("--\nModel Technologie\n129 Liberté 6 extension, Dakar");
        assertThat(text).doesNotContain("<").doesNotContain("**");
    }

    @Test
    void aMessageWithoutSignatureOrButtonStaysValid() {
        EmailRenderer.Rendered r = renderer.render(EmailContent.builder("Note interne").paragraph("Rien à signaler.").noSignature().build());

        assertThat(r.html()).contains("Rien à signaler.").doesNotContain("À bientôt").doesNotContain("Si le bouton ne fonctionne pas");
        assertThat(r.text()).doesNotContain("À bientôt");
    }
}
