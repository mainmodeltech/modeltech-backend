package com.modeltech.datamasteryhub.modules.communication.service;

public interface RecaptchaService {

    /**
     * Vérifie le token reCAPTCHA retourné par le frontend.
     *
     * @param token  Token généré par grecaptcha.execute() côté client
     * @return true si humain probable (score >= seuil), false sinon
     */
    boolean verify(String token);

    /**
     * Comme {@link #verify(String)}, en exigeant en plus que le jeton ait été généré pour l'action attendue
     * (ex. {@code bootcamp_register}) : un jeton obtenu sur un autre formulaire est refusé. Les noms d'hôte
     * autorisés sont contrôlés si {@code app.recaptcha.allowed-hostnames} est renseigné.
     */
    boolean verify(String token, String expectedAction);

}
