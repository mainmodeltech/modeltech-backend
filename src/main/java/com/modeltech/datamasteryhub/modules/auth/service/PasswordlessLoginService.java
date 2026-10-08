package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;

/**
 * Connexion sans mot de passe : un lien et un code à 6 chiffres, envoyés par les canaux de messagerie
 * activés (e-mail aujourd'hui ; WhatsApp se branchera sur le même envoi).
 */
public interface PasswordlessLoginService {

    /**
     * Envoie le lien et le code si un compte actif correspond. <strong>Ne révèle jamais</strong> si le compte existe
     * (même réponse, envoi en tâche de fond) ; au plus une demande par minute et par adresse.
     */
    void request(String email);

    /** Échange le jeton du lien contre une session. 400 si invalide, expiré ou déjà utilisé. */
    AuthResponse verifyLink(String token);

    /** Échange le code contre une session. 5 essais au plus par demande, puis la demande est annulée. */
    AuthResponse verifyCode(String email, String code);
}
