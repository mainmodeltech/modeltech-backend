package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.GoogleLoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.PasswordlessRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.PasswordlessVerifyRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthOptionsResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.response.MessageResponse;
import com.modeltech.datamasteryhub.modules.auth.service.AuthOutcome;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import com.modeltech.datamasteryhub.modules.auth.service.ClientInfo;
import com.modeltech.datamasteryhub.modules.auth.service.GoogleLoginService;
import com.modeltech.datamasteryhub.modules.auth.service.PasswordlessLoginService;
import com.modeltech.datamasteryhub.security.RefreshCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion, double authentification, session, mot de passe")
public class AuthController {

    private final AuthService authService;
    private final IpRateLimiter rateLimiter;
    private final PasswordlessLoginService passwordlessLoginService;
    private final GoogleLoginService googleLoginService;
    private final RefreshCookie refreshCookie;

    // ─────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Connexion (back-office ou apprenant)",
            description = "Apprenant : jeton d'accès de 15 min + cookie de session. Personnel : défi de double authentification "
                    + "(mfaRequired / mfaEnrollmentRequired + mfaToken) à valider sur /auth/mfa/*.")
    @ApiResponse(responseCode = "200", description = "Connexion réussie ou second facteur requis")
    @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest, HttpServletResponse response) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);   // freine la force brute
        return respond(authService.login(request, ClientInfo.from(httpRequest)), response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // MODES DE CONNEXION ALTERNATIFS (lien / code / Google)
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/options")
    @Operation(summary = "Modes de connexion disponibles")
    public ResponseEntity<AuthOptionsResponse> options() {
        return ResponseEntity.ok(googleLoginService.options());
    }

    @PostMapping("/passwordless/request")
    @Operation(summary = "Recevoir un lien et un code de connexion",
            description = "Toujours 200 (pas d'énumération de comptes) ; une demande par minute et par adresse.")
    public ResponseEntity<MessageResponse> passwordlessRequest(
            @Valid @RequestBody PasswordlessRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "passwordless-request");   // envoie des messages
        passwordlessLoginService.request(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "Si un compte correspond à cette adresse, un lien et un code de connexion viennent d'être envoyés."));
    }

    @PostMapping("/passwordless/verify")
    @Operation(summary = "Se connecter avec le lien ou le code reçu")
    @ApiResponse(responseCode = "200", description = "Connexion réussie ou second facteur requis")
    @ApiResponse(responseCode = "400", description = "Lien ou code invalide, expiré ou déjà utilisé")
    public ResponseEntity<AuthResponse> passwordlessVerify(
            @Valid @RequestBody PasswordlessVerifyRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);
        ClientInfo client = ClientInfo.from(httpRequest);
        boolean byLink = request.getToken() != null && !request.getToken().isBlank();
        return respond(byLink
                ? passwordlessLoginService.verifyLink(request.getToken(), client)
                : passwordlessLoginService.verifyCode(request.getEmail(), request.getCode(), client), response);
    }

    @PostMapping("/google")
    @Operation(summary = "Se connecter avec Google",
            description = "Le compte doit déjà exister et être actif ; aucun compte n'est créé par cette route.")
    @ApiResponse(responseCode = "200", description = "Connexion réussie ou second facteur requis")
    @ApiResponse(responseCode = "401", description = "Jeton Google invalide ou aucun compte correspondant")
    @ApiResponse(responseCode = "404", description = "Connexion Google non activée sur ce serveur")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse response) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);
        return respond(googleLoginService.login(request.getCredential(), ClientInfo.from(httpRequest)), response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // SESSION : rafraîchissement et déconnexion (cookie HttpOnly)
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Nouveau jeton d'accès à partir du cookie de session",
            description = "Le jeton de rafraîchissement (cookie HttpOnly) change à chaque appel. 401 : session absente, expirée, révoquée ou rejouée.")
    @ApiResponse(responseCode = "200", description = "Nouveau jeton d'accès")
    @ApiResponse(responseCode = "401", description = "Aucune session valide")
    public ResponseEntity<?> refresh(HttpServletRequest httpRequest, HttpServletResponse response) {
        refreshCookie.requireTrustedOrigin(httpRequest);
        rateLimiter.check(httpRequest, IpRateLimiter.REFRESH_SCOPE);
        String token = refreshCookie.read(httpRequest);
        Optional<AuthOutcome> outcome = token == null ? Optional.empty() : authService.refresh(token);
        if (outcome.isEmpty()) {
            if (token != null) refreshCookie.clear(response);
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).body(Map.of("message", "Session expirée."));
        }
        return respond(outcome.get(), response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion — termine la session et révoque le jeton d'accès")
    @ApiResponse(responseCode = "200", description = "Déconnecté")
    public ResponseEntity<MessageResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        refreshCookie.requireTrustedOrigin(httpRequest);
        authService.logout(extractBearerToken(authHeader), refreshCookie.read(httpRequest));
        refreshCookie.clear(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MessageResponse("Déconnecté avec succès"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // ME
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Profil du compte connecté",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Profil récupéré")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    public ResponseEntity<AuthResponse.AdminUserResponse> me() {
        return ResponseEntity.ok(authService.me(getAuthenticatedEmail()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHANGE PASSWORD (utilisateur connecté)
    // ─────────────────────────────────────────────────────────────────────

    @PutMapping("/change-password")
    @Operation(summary = "Changer le mot de passe",
            description = "Toutes les sessions du compte sont terminées : il faut se reconnecter.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Mot de passe modifié")
    @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect ou nouveau mot de passe refusé")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletResponse response) {
        authService.changePassword(getAuthenticatedEmail(), request);
        refreshCookie.clear(response);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // FORGOT PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Demande de réinitialisation du mot de passe",
            description = "Envoie un email avec un lien de réinitialisation. " +
                    "Retourne toujours 200 pour éviter l'énumération d'emails.")
    @ApiResponse(responseCode = "200", description = "Email envoyé (si le compte existe)")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        rateLimiter.check(httpRequest, "forgot-password"); // endpoint qui envoie des e-mails
        authService.forgotPassword(request);
        // Toujours le même message — sécurité anti-énumération
        return ResponseEntity.ok(new MessageResponse(
                "Si un compte correspond à cet email, un lien de réinitialisation a été envoyé."));
    }

    // ─────────────────────────────────────────────────────────────────────
    // RESET PASSWORD
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe avec un token")
    @ApiResponse(responseCode = "200", description = "Mot de passe réinitialisé")
    @ApiResponse(responseCode = "400", description = "Token invalide ou expiré, ou mot de passe refusé")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        rateLimiter.check(httpRequest, "reset-password");
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Mot de passe réinitialisé avec succès"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS PRIVÉS
    // ─────────────────────────────────────────────────────────────────────

    /** Réponse d'ouverture de session : jamais mise en cache, cookie de session déposé si le serveur en a émis un. */
    private ResponseEntity<AuthResponse> respond(AuthOutcome outcome, HttpServletResponse response) {
        if (outcome.refreshToken() != null) {
            refreshCookie.write(response, outcome.refreshToken(), outcome.refreshMaxAge());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(outcome.body());
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
