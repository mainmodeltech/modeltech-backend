package com.modeltech.datamasteryhub.modules.communication.service;

public interface RecaptchaService {

    /**
     * Vérifie le token reCAPTCHA retourné par le frontend.
     *
     * @param token  Token généré par grecaptcha.execute() côté client
     * @return true si humain probable (score >= seuil), false sinon
     */
    boolean verify(String token);

}
