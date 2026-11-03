package com.modeltech.datamasteryhub.modules.legal.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AcceptanceView;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AdminDocument;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.AdminOverview;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.Publish;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.SaveDraft;
import com.modeltech.datamasteryhub.modules.legal.service.LegalService;
import com.modeltech.datamasteryhub.security.Actor;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Textes légaux (administration). Réservé à la Direction (droit LEGAL_MANAGE, voir AdminAccessRules). */
@RestController
@RequestMapping("/api/v1/admin/legal")
@RequiredArgsConstructor
@Tag(name = "Admin - Textes légaux")
public class AdminLegalController {

    private final LegalService legal;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminOverview>>> overview() {
        return ResponseEntity.ok(ApiResponse.ok("Textes légaux", legal.overview()));
    }

    @PutMapping("/{code}/draft")
    public ResponseEntity<ApiResponse<AdminDocument>> saveDraft(@PathVariable LegalCode code, @Valid @RequestBody SaveDraft body) {
        return ResponseEntity.ok(ApiResponse.ok("Brouillon enregistré", legal.saveDraft(code, body, Actor.email())));
    }

    @DeleteMapping("/{code}/draft")
    public ResponseEntity<ApiResponse<Void>> discardDraft(@PathVariable LegalCode code) {
        legal.discardDraft(code);
        return ResponseEntity.ok(ApiResponse.ok("Brouillon abandonné", null));
    }

    @PostMapping("/{code}/publish")
    public ResponseEntity<ApiResponse<AdminDocument>> publish(@PathVariable LegalCode code, @Valid @RequestBody Publish body) {
        return ResponseEntity.ok(ApiResponse.ok("Texte publié", legal.publish(code, body, Actor.email())));
    }

    @GetMapping("/acceptances")
    public ResponseEntity<ApiResponse<List<AcceptanceView>>> acceptances(@RequestParam(required = false) String email,
                                                                         @RequestParam(required = false) LegalCode code,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        var result = legal.acceptances(email, code, page, size);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " acceptation(s)", result));
    }
}
