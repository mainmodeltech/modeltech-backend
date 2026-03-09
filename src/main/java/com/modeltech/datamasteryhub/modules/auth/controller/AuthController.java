package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ForgotPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.ResetPasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.response.MessageResponse;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Login, logout et gestion du compte admin")
public class AuthController {

    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Connexion administrateur", description = "Retourne un JWT valide 24h")
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ─────────────────────────────────────────────────────────────────────
    // ME
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Profil de l'administrateur connecté",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Profil récupéré")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    public ResponseEntity<AuthResponse.AdminUserResponse> me() {
        return ResponseEntity.ok(authService.me(getAuthenticatedEmail()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOGOUT — révoque le token JWT
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion — révoque le JWT",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Déconnecté avec succès")
    public ResponseEntity<MessageResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractBearerToken(authHeader);
        authService.logout(token);
        return ResponseEntity.ok(new MessageResponse("Déconnecté avec succès"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHANGE PASSWORD (utilisateur connecté)
    // ─────────────────────────────────────────────────────────────────────

    @PutMapping("/change-password")
    @Operation(summary = "Changer le mot de passe",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Mot de passe modifié")
    @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(getAuthenticatedEmail(), request);
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
            @Valid @RequestBody ForgotPasswordRequest request) {

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
    @ApiResponse(responseCode = "400", description = "Token invalide ou expiré")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Mot de passe réinitialisé avec succès"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS PRIVÉS
    // ─────────────────────────────────────────────────────────────────────

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