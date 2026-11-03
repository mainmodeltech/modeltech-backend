package com.modeltech.datamasteryhub.modules.legal;

/** Les textes légaux de la plateforme. {@code slug} est la fin de l'adresse publique de la page. */
public enum LegalCode {
    CGU("Conditions générales d'utilisation", "cgu"),
    CGV("Conditions générales de vente", "cgv"),
    PRIVACY("Politique de confidentialité et cookies", "confidentialite"),
    MENTIONS("Mentions légales", "mentions-legales");

    private final String title;
    private final String slug;

    LegalCode(String title, String slug) {
        this.title = title;
        this.slug = slug;
    }

    public String title() {
        return title;
    }

    public String slug() {
        return slug;
    }
}
