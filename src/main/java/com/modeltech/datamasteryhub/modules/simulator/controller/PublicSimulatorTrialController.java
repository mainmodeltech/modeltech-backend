package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.simulator.dto.TrialPayloads.TrialRequest;
import com.modeltech.datamasteryhub.modules.simulator.service.TrialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator/trial")
@RequiredArgsConstructor
@Tag(name = "Formulaires (Public)", description = "Formulaires du site")
public class PublicSimulatorTrialController {

    private final TrialService trial;
    private final IpRateLimiter rateLimiter;

    @PostMapping
    @Operation(summary = "Demander l'essai gratuit du simulateur",
            description = "Crée un compte apprenant sans mot de passe, lui accorde un essai (un mini-test) et envoie un lien de connexion. "
                    + "Réponse identique dans tous les cas. Limité par IP.")
    public ResponseEntity<ApiResponse<Void>> request(@Valid @RequestBody TrialRequest request, HttpServletRequest http) {
        rateLimiter.check(http, "simulator-trial");
        trial.start(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "C'est parti ! Un lien de connexion vient d'être envoyé à votre adresse e-mail (pensez à vérifier vos courriers indésirables).", null));
    }
}
