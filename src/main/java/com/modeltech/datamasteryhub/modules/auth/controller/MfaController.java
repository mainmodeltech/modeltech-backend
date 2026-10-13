package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AuthResponse;
import com.modeltech.datamasteryhub.modules.auth.service.AuthOutcome;
import com.modeltech.datamasteryhub.modules.auth.service.ClientInfo;
import com.modeltech.datamasteryhub.modules.auth.service.MfaLoginService;
import com.modeltech.datamasteryhub.modules.auth.service.MfaService;
import com.modeltech.datamasteryhub.security.RefreshCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Double authentification du personnel (TOTP). Les trois premières routes sont publiques mais exigent le défi
 * ({@code mfaToken}) délivré par la connexion ; la dernière demande une session ouverte.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
@Tag(name = "Authentification — double authentification")
public class MfaController {

    private final MfaLoginService mfaLogin;
    private final IpRateLimiter rateLimiter;
    private final RefreshCookie refreshCookie;

    public record VerifyRequest(@NotBlank String mfaToken, @NotBlank @Size(max = 32) String code) {}

    public record EnrollRequest(@NotBlank String mfaToken) {}

    public record RecoveryRequest(@NotBlank @Size(max = 32) String code) {}

    @PostMapping("/verify")
    @Operation(summary = "Valider le code de l'application d'authentification (ou un code de secours)")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse response) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);
        return respond(mfaLogin.verify(request.mfaToken(), request.code(), ClientInfo.from(httpRequest)), response);
    }

    @PostMapping("/enroll/start")
    @Operation(summary = "Première connexion : obtenir le secret à enregistrer dans l'application d'authentification")
    public ResponseEntity<MfaService.Enrollment> enrollStart(@Valid @RequestBody EnrollRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mfaLogin.startEnrollment(request.mfaToken()));
    }

    @PostMapping("/enroll/confirm")
    @Operation(summary = "Activer la double authentification avec un premier code ; renvoie les codes de secours (une seule fois) et ouvre la session")
    public ResponseEntity<AuthResponse> enrollConfirm(@Valid @RequestBody VerifyRequest request,
                                                      HttpServletRequest httpRequest, HttpServletResponse response) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);
        return respond(mfaLogin.confirmEnrollment(request.mfaToken(), request.code(), ClientInfo.from(httpRequest)), response);
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Générer de nouveaux codes de secours (les précédents sont invalidés) ; exige un code valide",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, List<String>>> regenerate(@Valid @RequestBody RecoveryRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, IpRateLimiter.LOGIN_SCOPE);
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("recoveryCodes", mfaLogin.regenerateRecoveryCodes(email, request.code())));
    }

    private ResponseEntity<AuthResponse> respond(AuthOutcome outcome, HttpServletResponse response) {
        if (outcome.refreshToken() != null) refreshCookie.write(response, outcome.refreshToken(), outcome.refreshMaxAge());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(outcome.body());
    }
}
