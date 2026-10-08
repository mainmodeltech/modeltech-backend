package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;

/** Authentification des comptes de back-office ({@code admin_users}) et des apprenants ({@code learners}). */
public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse.AdminUserResponse me(String email);

    /**
     * Ouvre une session pour un compte actif dont l'identité vient d'être prouvée autrement que par mot de passe
     * (lien/code reçu, jeton Google vérifié). Compte inconnu : {@code UsernameNotFoundException}.
     */
    AuthResponse loginWithoutPassword(String email);

    /**
     * Déconnexion : révoque le token JWT en le mettant en blacklist.
     *
     * @param token le Bearer token extrait du header Authorization
     */
    void logout(String token);

    void changePassword(String email, ChangePasswordRequest request);

    /**
     * Génère un token de réinitialisation et envoie un email.
     * Retourne toujours un message générique (sécurité : pas d'énumération d'emails).
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Valide le token de réinitialisation et change le mot de passe.
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Émet un jeton « définir / réinitialiser mon mot de passe » (invalide les précédents)
     * — utilisé par les invitations de comptes. À utiliser avec {@link #passwordSetupLink}.
     */
    String createPasswordResetToken(String email, int validityMinutes);

    /** Lien frontend de définition de mot de passe, adapté au type de compte. */
    String passwordSetupLink(String token, boolean learner);
}
