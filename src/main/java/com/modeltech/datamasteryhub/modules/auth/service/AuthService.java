package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse.AdminUserResponse me(String email);

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
}