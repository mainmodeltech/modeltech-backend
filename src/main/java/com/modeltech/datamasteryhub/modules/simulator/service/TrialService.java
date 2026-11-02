package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
import com.modeltech.datamasteryhub.modules.auth.service.PasswordlessLoginService;
import com.modeltech.datamasteryhub.modules.communication.service.ContactMessageService;
import com.modeltech.datamasteryhub.modules.simulator.dto.TrialPayloads.TrialRequest;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimAccessGrant;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimCertification;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimAccessGrantRepository;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimCertificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Essai gratuit, produit d'appel du simulateur : le visiteur laisse son nom et son e-mail, un compte apprenant est créé
 * (sans mot de passe) avec un droit « Essai » (un seul mini-test, quelques jours), et il reçoit un lien de connexion à usage
 * unique. Le droit n'est utilisable qu'après s'être connecté avec ce lien : cela vérifie l'adresse e-mail.
 *
 * <p>La réponse est toujours la même, que l'adresse soit nouvelle, déjà connue ou refusée (pas d'énumération de comptes).
 * Un seul essai par personne : garanti par la base (index unique) et vérifié ici.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialService {

    private final LearnerService learnerService;
    private final PasswordlessLoginService passwordless;
    private final SimAccessGrantRepository grants;
    private final SimCertificationRepository certifications;
    private final ContactMessageService contacts;

    @Value("${app.simulator.trial-days:7}")
    private int trialDays;

    @Transactional
    public void start(TrialRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (DisposableEmails.isDisposable(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utilisez une adresse e-mail durable (les adresses jetables ne sont pas acceptées).");
        }
        Learner learner;
        try {
            learner = learnerService.findOrCreateWithoutInvitation(req.firstName(), req.lastName(), email, req.phone());
        } catch (ResponseStatusException e) {
            log.info("Essai gratuit ignoré (adresse non éligible) : {}", e.getReason());
            return;   // même réponse publique : on ne révèle pas pourquoi
        }

        if (!grants.existsByLearnerIdAndPlanCode(learner.getId(), PlanCode.TRIAL)) {
            SimCertification certification = certifications.findByCodeAndActiveTrue(QuestionBankService.CERTIFICATION)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le simulateur n'est pas encore disponible."));
            LocalDateTime now = LocalDateTime.now();
            SimAccessGrant g = new SimAccessGrant();
            g.setLearner(learner);
            g.setCertification(certification);
            g.setPlanCode(PlanCode.TRIAL);
            g.setStartsAt(now);
            g.setExpiresAt(now.plusDays(trialDays));
            g.setExamAttemptsTotal(1);
            g.setGrantedBy("essai-gratuit");
            g.setReason("Essai gratuit demandé sur le site");
            grants.save(g);
            contacts.saveSimulatorTrial(req.firstName(), req.lastName(), email, req.phone());   // le prospect apparaît dans « Messages »
        }
        passwordless.request(email);   // lien + code de connexion (au plus un par minute et par adresse)
    }
}
