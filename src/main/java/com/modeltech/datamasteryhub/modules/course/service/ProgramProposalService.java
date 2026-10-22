package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.ProposalPayloads;
import com.modeltech.datamasteryhub.modules.course.enums.ProposalStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Propositions de modification du programme : le formateur propose (formations dont une session lui est confiée, sans rien
 * supprimer d'existant), la pédagogie ({@code PROGRAM_MANAGE} + {@code SCOPE_ALL_FORMATIONS}) valide d'un bloc, demande des
 * modifications ou refuse. La validation FUSIONNE la proposition dans le programme en ligne actuel, sans rien écraser.
 */
public interface ProgramProposalService {

    // ── Formateur ──

    List<ProposalPayloads.ProposableFormation> myFormations(String actorEmail);

    /** La proposition en cours, sinon une copie du programme en ligne (id nul). */
    ProposalPayloads.Proposal getMine(UUID formationId, String actorEmail);

    /** Crée ou met à jour la proposition en cours (DRAFT ou CHANGES_REQUESTED ; sinon 409). */
    ProposalPayloads.Proposal saveMine(UUID formationId, ProposalPayloads.Body body, String actorEmail);

    ProposalPayloads.Proposal submit(UUID formationId, String actorEmail);

    ProposalPayloads.Proposal withdraw(UUID formationId, String actorEmail);

    // ── Pédagogie ──

    List<ProposalPayloads.Summary> list(ProposalStatus status, Collection<String> actorAuthorities);

    long pendingCount(String actorEmail, Collection<String> actorAuthorities);

    ProposalPayloads.Proposal detail(UUID id, String actorEmail, Collection<String> actorAuthorities);

    ProposalPayloads.Proposal approve(UUID id, ProposalPayloads.Decision decision, String actorEmail, Collection<String> actorAuthorities);

    ProposalPayloads.Proposal requestChanges(UUID id, ProposalPayloads.Decision decision, String actorEmail, Collection<String> actorAuthorities);

    ProposalPayloads.Proposal reject(UUID id, ProposalPayloads.Decision decision, String actorEmail, Collection<String> actorAuthorities);
}
