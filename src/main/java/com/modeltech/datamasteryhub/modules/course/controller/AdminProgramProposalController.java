package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.modules.course.dto.ProposalPayloads;
import com.modeltech.datamasteryhub.modules.course.enums.ProposalStatus;
import com.modeltech.datamasteryhub.modules.course.service.ProgramProposalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Propositions de modification du programme. {@code /mine/**} : le formateur (droit {@code PROGRAM_PROPOSE}) ; le reste :
 * la relecture par la pédagogie ({@code PROGRAM_MANAGE}, portée {@code SCOPE_ALL_FORMATIONS} vérifiée par le service).
 * Réponses en JSON brut (sans enveloppe {@code ApiResponse}), comme le programme lui-même.
 */
@RestController
@RequestMapping("/api/v1/admin/program-proposals")
@RequiredArgsConstructor
@Tag(name = "Admin - Propositions de modification du programme")
public class AdminProgramProposalController {

    private final ProgramProposalService proposalService;

    // ── Formateur ──

    @GetMapping("/mine/formations")
    public List<ProposalPayloads.ProposableFormation> myFormations(Authentication authentication) {
        return proposalService.myFormations(authentication.getName());
    }

    @GetMapping("/mine/{formationId}")
    public ProposalPayloads.Proposal getMine(@PathVariable UUID formationId, Authentication authentication) {
        return proposalService.getMine(formationId, authentication.getName());
    }

    @PutMapping("/mine/{formationId}")
    public ProposalPayloads.Proposal saveMine(@PathVariable UUID formationId, @RequestBody ProposalPayloads.Body body,
                                              Authentication authentication) {
        return proposalService.saveMine(formationId, body, authentication.getName());
    }

    @PostMapping("/mine/{formationId}/submit")
    public ProposalPayloads.Proposal submit(@PathVariable UUID formationId, Authentication authentication) {
        return proposalService.submit(formationId, authentication.getName());
    }

    @PostMapping("/mine/{formationId}/withdraw")
    public ProposalPayloads.Proposal withdraw(@PathVariable UUID formationId, Authentication authentication) {
        return proposalService.withdraw(formationId, authentication.getName());
    }

    // ── Pédagogie ──

    @GetMapping
    public List<ProposalPayloads.Summary> list(@RequestParam(required = false) ProposalStatus status, Authentication authentication) {
        return proposalService.list(status, authorities(authentication));
    }

    @GetMapping("/pending-count")
    public ProposalPayloads.Count pendingCount(Authentication authentication) {
        return new ProposalPayloads.Count(proposalService.pendingCount(authentication.getName(), authorities(authentication)));
    }

    @GetMapping("/{id}")
    public ProposalPayloads.Proposal detail(@PathVariable UUID id, Authentication authentication) {
        return proposalService.detail(id, authentication.getName(), authorities(authentication));
    }

    @PostMapping("/{id}/approve")
    public ProposalPayloads.Proposal approve(@PathVariable UUID id, @RequestBody(required = false) ProposalPayloads.Decision decision,
                                             Authentication authentication) {
        return proposalService.approve(id, decision, authentication.getName(), authorities(authentication));
    }

    @PostMapping("/{id}/request-changes")
    public ProposalPayloads.Proposal requestChanges(@PathVariable UUID id, @RequestBody(required = false) ProposalPayloads.Decision decision,
                                                    Authentication authentication) {
        return proposalService.requestChanges(id, decision, authentication.getName(), authorities(authentication));
    }

    @PostMapping("/{id}/reject")
    public ProposalPayloads.Proposal reject(@PathVariable UUID id, @RequestBody(required = false) ProposalPayloads.Decision decision,
                                            Authentication authentication) {
        return proposalService.reject(id, decision, authentication.getName(), authorities(authentication));
    }

    private List<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
