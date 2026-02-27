package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.modules.auth.dto.request.ChangePasswordRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.request.LoginRequest;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Login et gestion du compte admin")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Connexion administrateur", description = "Retourne un JWT valide 24h")
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'administrateur connecté", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Profil récupéré")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    public ResponseEntity<AuthResponse.AdminUserResponse> me() {
        return ResponseEntity.ok(authService.me(getAuthenticatedEmail()));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Changer le mot de passe", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Mot de passe modifié")
    @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(getAuthenticatedEmail(), request);
        return ResponseEntity.noContent().build();
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
