package com.modeltech.datamasteryhub.modules.legal.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.legal.LegalContext;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AcceptRequest;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.DocumentRef;
import com.modeltech.datamasteryhub.modules.legal.service.LegalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Acceptation des conditions d'utilisation par l'apprenant connecté : à la première connexion et à chaque nouvelle version publiée, avant d'utiliser
 * l'espace (contenus protégés, banque de questions). Une version déjà acceptée n'est jamais redemandée.
 */
@RestController
@RequestMapping("/api/v1/learner/legal")
@RequiredArgsConstructor
@Tag(name = "Apprenant - Textes légaux")
public class LearnerLegalController {

    private final LegalService legal;
    private final LearnerRepository learners;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<DocumentRef>>> pending(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Textes à accepter", legal.pendingFor(LegalContext.LEARNER_ACCESS, authentication.getName())));
    }

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<List<DocumentRef>>> accept(Authentication authentication, @Valid @RequestBody AcceptRequest body) {
        Learner learner = learners.findByEmailIgnoreCaseAndIsDeletedFalse(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Apprenant", "email", authentication.getName()));
        legal.requireAccepted(LegalContext.LEARNER_ACCESS, body.documents());
        legal.record(LegalContext.LEARNER_ACCESS, "LEARNER", learner.getId().toString(), learner.getEmail(), learner.getFullName(), body.documents());
        return ResponseEntity.ok(ApiResponse.ok("Conditions acceptées", legal.pendingFor(LegalContext.LEARNER_ACCESS, authentication.getName())));
    }
}
