package com.modeltech.datamasteryhub.modules.auth.service;

import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.security.AppUserDetails;

import java.util.Optional;

/**
 * Authentification des comptes de back-office ({@code admin_users}) et des apprenants ({@code learners}).
 *
 * <p>Une session = un jeton d'accès de 15 minutes (en mémoire du navigateur) + un jeton de rafraîchissement dans un cookie
 * {@code HttpOnly}. Les comptes du personnel passent en plus par un second facteur (TOTP) : tant qu'il n'est pas validé,
 * la connexion ne renvoie qu'un défi ({@code mfaRequired} / {@code mfaEnrollmentRequired}) et aucun jeton d'accès.
 */
public interface AuthService {

    AuthOutcome login(LoginRequest request, ClientInfo client);

    AuthResponse.AdminUserResponse me(String email);

    /**
     * Ouvre une session pour un compte actif dont l'identité vient d'être prouvée autrement que par mot de passe
     * (lien/code reçu, jeton Google vérifié). Compte inconnu : {@code UsernameNotFoundException}.
     * Le personnel passe quand même par le second facteur.
     */
    AuthOutcome loginWithoutPassword(String email, ClientInfo client);

    /** Ouvre la session proprement dite (jetons) une fois tous les facteurs validés. */
    AuthOutcome startSession(AppUserDetails user, boolean mfaVerified, ClientInfo client);

    /** Échange un jeton de rafraîchissement contre un nouveau jeton d'accès ; vide si inconnu, expiré, révoqué ou rejoué. */
    Optional<AuthOutcome> refresh(String refreshToken);

    /**
     * Déconnexion : met fin à la session du jeton de rafraîchissement et révoque le jeton d'accès présenté.
     *
     * @param accessToken  le Bearer token (peut être absent ou expiré)
     * @param refreshToken le contenu du cookie (peut être absent)
     */
    void logout(String accessToken, String refreshToken);

    /** Change le mot de passe ; toutes les sessions du compte (y compris celle-ci) sont terminées. */
    void changePassword(String email, ChangePasswordRequest request);

    /**
     * Génère un token de réinitialisation et envoie un email.
     * Retourne toujours un message générique (sécurité : pas d'énumération d'emails).
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Valide le token de réinitialisation et change le mot de passe (les sessions ouvertes sont terminées).
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
