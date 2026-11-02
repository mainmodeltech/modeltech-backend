package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.simulator.dto.GrantPayloads.*;
import com.modeltech.datamasteryhub.modules.simulator.entity.GrantStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimAccessGrant;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimCertification;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimAccessGrantRepository;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimCertificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Droits d'usage du simulateur : octroi manuel (lot S2), le paiement automatique y branchera son propre octroi (lot S3).
 * Chaque octroi ou révocation est tracé dans le journal d'audit avec son motif.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessService {

    private final SimAccessGrantRepository grants;
    private final SimCertificationRepository certifications;
    private final LearnerRepository learners;
    private final AuditService audit;

    public Page<GrantView> list(int page, int size) {
        return grants.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))).map(AccessService::view);
    }

    @Transactional
    public GrantView grant(GrantRequest req, String actor) {
        Learner learner = learners.findByEmailIgnoreCaseAndIsDeletedFalse(req.learnerEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Apprenant", "e-mail", req.learnerEmail()));
        SimCertification certification = certifications.findByCodeAndActiveTrue(QuestionBankService.CERTIFICATION)
                .orElseThrow(() -> new ResourceNotFoundException("Certification", "code", QuestionBankService.CERTIFICATION));
        LocalDateTime now = LocalDateTime.now();

        SimAccessGrant g = new SimAccessGrant();
        g.setLearner(learner);
        g.setCertification(certification);
        g.setPlanCode(req.plan());
        g.setStartsAt(now);
        g.setExpiresAt(now.plusDays(req.days()));
        g.setExamAttemptsTotal(req.examAttempts());
        g.setGrantedBy(actor);
        g.setReason(req.reason().trim());
        SimAccessGrant saved = grants.save(g);
        audit.record(AuditAction.SIM_ACCESS_GRANTED, "SimAccessGrant", saved.getId(),
                "Accès au simulateur (" + req.plan() + ", " + req.days() + " jours, " + req.examAttempts() + " examen(s)) accordé à "
                        + learner.getFullName() + " : " + req.reason().trim());
        return view(saved);
    }

    @Transactional
    public GrantView revoke(UUID id, String reason, String actor) {
        SimAccessGrant g = grants.findById(id).orElseThrow(() -> new ResourceNotFoundException("Droit d'usage", "id", id));
        if (g.getStatus() == GrantStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce droit d'usage est déjà révoqué.");
        }
        g.setStatus(GrantStatus.REVOKED);
        g.setRevokedAt(LocalDateTime.now());
        g.setRevokedBy(actor);
        g.setRevokeReason(reason.trim());
        audit.record(AuditAction.SIM_ACCESS_REVOKED, "SimAccessGrant", id,
                "Accès au simulateur de " + g.getLearner().getFullName() + " révoqué : " + reason.trim());
        return view(grants.save(g));
    }

    static GrantView view(SimAccessGrant g) {
        return new GrantView(g.getId(), g.getLearner().getEmail(), g.getLearner().getFullName(), g.getPlanCode(), g.getStartsAt(),
                g.getExpiresAt(), g.getExamAttemptsTotal(), g.getExamAttemptsUsed(), g.getStatus(), g.getGrantedBy(), g.getReason(),
                g.getCreatedAt(), g.getRevokedAt(), g.getRevokeReason());
    }
}
