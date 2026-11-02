package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.simulator.dto.LearnerSimulatorPayloads.*;
import com.modeltech.datamasteryhub.modules.simulator.entity.*;
import com.modeltech.datamasteryhub.modules.simulator.repository.*;
import com.modeltech.datamasteryhub.modules.simulator.service.AttemptScorer.Bucket;
import com.modeltech.datamasteryhub.modules.simulator.service.AttemptScorer.Scoring;
import com.modeltech.datamasteryhub.modules.simulator.service.ExamAssembler.Picked;
import com.modeltech.datamasteryhub.modules.simulator.strategy.ItemTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Tentatives d'un apprenant. Le <b>serveur est autoritaire</b> : l'échéance, le quota d'examens et la notation vivent ici ;
 * une tentative n'est jamais lisible par un autre apprenant (identifiant d'un tiers = « introuvable ») ; la vue envoyée
 * pendant l'examen ne contient aucun corrigé. Un examen dont le temps est écoulé est remis et noté automatiquement
 * (à la prochaine requête ou par le balayage planifié).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AttemptService {

    private final LearnerRepository learners;
    private final SimBlueprintRepository blueprints;
    private final SimAccessGrantRepository grants;
    private final SimExamFormRepository forms;
    private final SimAttemptRepository attempts;
    private final SimAttemptAnswerRepository answers;
    private final SimAttemptEventRepository events;
    private final SimItemVersionRepository itemVersions;
    private final ItemTypeRegistry registry;
    private final ExamAssembler assembler;
    private final AttemptScorer scorer;

    /** Taille d'un examen chronométré (le guide annonce 40 à 60 questions). */
    @Value("${app.simulator.exam-questions:50}")
    private int examQuestions;

    /** Marge réseau acceptée après l'échéance. */
    @Value("${app.simulator.grace-seconds:5}")
    private int graceSeconds;

    @Value("${app.simulator.study-default:10}")
    private int studyDefault;

    @Value("${app.simulator.study-max:30}")
    private int studyMax;

    // ── Catalogue et accès ──────────────────────────────────────────

    public CatalogView catalog(String email) {
        Learner learner = learner(email);
        SimBlueprint bp = blueprint();
        Map<UUID, Long> published = new HashMap<>();
        for (Object[] row : itemVersions.countBySkillAndStatus()) {
            if (row[1] == ItemStatus.PUBLISHED) published.put((UUID) row[0], (Long) row[2]);
        }
        List<DomainInfo> domains = bp.getDomains().stream().map(d -> new DomainInfo(d.getCode(), d.getName(), d.getWeightMin(), d.getWeightMax(),
                (int) d.getSkills().stream().mapToLong(s -> published.getOrDefault(s.getId(), 0L)).sum())).toList();
        List<AccessView> access = grants.findUsable(learner.getId(), LocalDateTime.now()).stream().map(AttemptService::access).toList();
        boolean inProgress = attempts.findFirstByLearnerIdAndModeAndStatus(learner.getId(), AttemptMode.EXAM, AttemptStatus.IN_PROGRESS).isPresent();
        return new CatalogView(bp.getCertification().getCode(), bp.getCertification().getName(), bp.getLabel(), bp.getDurationMinutes(),
                examQuestions, bp.getPassScore(), domains, access, inProgress);
    }

    // ── Démarrage ───────────────────────────────────────────────────

    @Transactional
    public AttemptView start(String email, StartRequest req) {
        Learner learner = learner(email);
        LocalDateTime now = LocalDateTime.now();
        List<SimAccessGrant> usable = grants.findUsable(learner.getId(), now);
        if (usable.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'avez pas d'accès actif au simulateur.");
        }
        SimBlueprint bp = blueprint();
        SimAccessGrant grant;
        List<Picked> picked;
        LocalDateTime deadline = null;

        if (req.mode() == AttemptMode.EXAM) {
            attempts.findFirstByLearnerIdAndModeAndStatus(learner.getId(), AttemptMode.EXAM, AttemptStatus.IN_PROGRESS).ifPresent(open -> {
                if (isOverdue(open, now)) {
                    finish(open, AttemptStatus.EXPIRED);
                    attempts.flush();   // libère l'index « un seul examen en cours » avant d'en insérer un nouveau
                } else {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un examen est déjà en cours : reprenez-le (" + open.getId() + ").");
                }
            });
            grant = usable.stream().filter(g -> g.examAttemptsRemaining() > 0).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez utilisé tous les examens de votre forfait."));
            picked = assembler.assembleExam(bp, examQuestions, learner.getId());
            // Consommation atomique : deux démarrages simultanés ne dépassent jamais le quota
            if (grants.consumeExamAttempt(grant.getId(), now) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez utilisé tous les examens de votre forfait.");
            }
            deadline = now.plusMinutes(bp.getDurationMinutes());
        } else {
            grant = usable.get(0);
            int count = req.count() == null ? studyDefault : Math.min(req.count(), studyMax);
            picked = assembler.assembleStudy(bp, req.domain(), req.skill(), count, learner.getId());
        }

        SimExamForm form = new SimExamForm();
        form.setBlueprint(bp);
        form.setMode(req.mode());
        int position = 1;
        for (Picked p : picked) {
            SimExamFormItem item = new SimExamFormItem();
            item.setForm(form);
            item.setPosition(position++);
            item.setItemVersion(p.version());
            item.setDomainCode(p.domainCode());
            item.setSkillCode(p.skillCode());
            form.getItems().add(item);
        }
        forms.save(form);

        SimAttempt attempt = new SimAttempt();
        attempt.setLearner(learner);
        attempt.setGrant(grant);
        attempt.setForm(form);
        attempt.setMode(req.mode());
        attempt.setStartedAt(now);
        attempt.setDeadlineAt(deadline);
        attempts.save(attempt);
        event(attempt, "START", Map.of("questions", picked.size()));
        return view(attempt, List.of());
    }

    // ── Lecture, réponses, remise ───────────────────────────────────

    @Transactional
    public AttemptView get(String email, UUID id) {
        SimAttempt attempt = owned(email, id);
        expireIfOverdue(attempt);
        return view(attempt, answers.findAllByAttemptId(attempt.getId()));
    }

    @Transactional
    public AnswerResult saveAnswer(String email, UUID id, UUID ref, AnswerRequest req) {
        SimAttempt attempt = owned(email, id);
        if (expireIfOverdue(attempt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le temps est écoulé : votre examen a été remis automatiquement.");
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tentative est terminée.");
        }
        SimExamFormItem formItem = attempt.getForm().getItems().stream().filter(i -> i.getId().equals(ref)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question", "ref", ref));
        SimItemVersion v = formItem.getItemVersion();
        Map<String, Object> normalized = registry.normalizeAnswer(v.getType(), v.getPayload(), req.answer());

        SimAttemptAnswer row = answers.findByAttemptIdAndFormItemId(attempt.getId(), formItem.getId()).orElseGet(() -> {
            SimAttemptAnswer a = new SimAttemptAnswer();
            a.setAttempt(attempt);
            a.setFormItem(formItem);
            return a;
        });
        row.setAnswer(normalized);
        if (req.flagged() != null) row.setFlagged(req.flagged());
        if (req.timeSpentSeconds() != null) row.setTimeSpentSeconds(req.timeSpentSeconds());
        row.setUpdatedAt(LocalDateTime.now());
        answers.save(row);

        Feedback feedback = null;
        if (attempt.getMode() == AttemptMode.STUDY) {    // correction immédiate : étude uniquement, jamais en examen
            feedback = new Feedback(scorer.scoreItem(formItem, normalized), registry.correctAnswer(v.getType(), v.getPayload()), v.getExplanationFr());
        }
        return new AnswerResult(true, feedback);
    }

    @Transactional
    public ReportView submit(String email, UUID id) {
        SimAttempt attempt = owned(email, id);
        expireIfOverdue(attempt);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) finish(attempt, AttemptStatus.SUBMITTED);
        return report(attempt);
    }

    public ReportView report(String email, UUID id) {
        SimAttempt attempt = owned(email, id);
        if (!attempt.getStatus().isFinished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le rapport est disponible une fois la tentative terminée.");
        }
        return report(attempt);
    }

    public List<AttemptSummary> history(String email, int limit) {
        Learner learner = learner(email);
        return attempts.findByLearnerIdOrderByStartedAtDesc(learner.getId(), PageRequest.of(0, Math.min(Math.max(1, limit), 50)))
                .stream().map(AttemptService::summary).toList();
    }

    // ── Balayage des examens dont le temps est écoulé ───────────────

    @Scheduled(fixedDelayString = "${app.simulator.sweep-ms:60000}", initialDelayString = "${app.simulator.sweep-ms:60000}")
    @Transactional
    public void expireOverdue() {
        List<SimAttempt> overdue = attempts.findOverdue(LocalDateTime.now().minusSeconds(graceSeconds));
        for (SimAttempt a : overdue) {
            try {
                finish(a, AttemptStatus.EXPIRED);
            } catch (RuntimeException e) {
                log.warn("Remise automatique impossible pour la tentative {} : {}", a.getId(), e.getMessage());
            }
        }
        if (!overdue.isEmpty()) log.info("{} tentative(s) du simulateur remise(s) automatiquement (temps écoulé)", overdue.size());
    }

    // ── Privé ───────────────────────────────────────────────────────

    private boolean isOverdue(SimAttempt a, LocalDateTime now) {
        return a.getStatus() == AttemptStatus.IN_PROGRESS && a.getDeadlineAt() != null && now.isAfter(a.getDeadlineAt().plusSeconds(graceSeconds));
    }

    /** Remet et note une tentative dont le temps est écoulé ; renvoie vrai si c'est ce qui vient d'arriver. */
    private boolean expireIfOverdue(SimAttempt a) {
        if (isOverdue(a, LocalDateTime.now())) {
            finish(a, AttemptStatus.EXPIRED);
            return true;
        }
        return false;
    }

    private void finish(SimAttempt a, AttemptStatus status) {
        SimBlueprint bp = a.getForm().getBlueprint();
        Scoring s = scorer.score(bp, a.getForm().getItems(), answers.findAllByAttemptId(a.getId()));
        a.setStatus(status);
        a.setFinishedAt(status == AttemptStatus.EXPIRED && a.getDeadlineAt() != null ? a.getDeadlineAt() : LocalDateTime.now());
        a.setScorePercent(BigDecimal.valueOf(s.percent()));
        a.setScaledScore(s.scaled());
        a.setPassed(a.getMode() == AttemptMode.EXAM ? s.scaled() >= bp.getPassScore() : null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domains", s.domains().stream().map(AttemptService::bucketMap).toList());
        result.put("skills", s.skills().stream().map(AttemptService::bucketMap).toList());
        a.setResult(result);
        attempts.save(a);
        event(a, status == AttemptStatus.EXPIRED ? "EXPIRE" : "SUBMIT", Map.of("scaledScore", s.scaled()));
    }

    private ReportView report(SimAttempt a) {
        SimBlueprint bp = a.getForm().getBlueprint();
        List<SimAttemptAnswer> given = answers.findAllByAttemptId(a.getId());
        Scoring s = scorer.score(bp, a.getForm().getItems(), given);
        Map<UUID, SimAttemptAnswer> byItem = new HashMap<>();
        given.forEach(x -> byItem.put(x.getFormItem().getId(), x));

        List<QuestionReport> questions = new ArrayList<>();
        for (SimExamFormItem item : a.getForm().getItems()) {
            SimItemVersion v = item.getItemVersion();
            SimAttemptAnswer ans = byItem.get(item.getId());
            Map<String, Object> your = ans == null ? null : ans.getAnswer();
            questions.add(new QuestionReport(item.getPosition(), item.getDomainCode(), item.getSkillCode(), v.getItem().getPublicCode(), v.getType(),
                    v.getStemEn(), v.getPayload(), your, registry.correctAnswer(v.getType(), v.getPayload()), scorer.scoreItem(item, your),
                    v.getExplanationFr(), ans == null ? 0 : ans.getTimeSpentSeconds()));
        }
        return new ReportView(summary(a), bp.getPassScore(), true, s.domains().stream().map(AttemptService::bucketView).toList(),
                s.skills().stream().map(AttemptService::bucketView).toList(), questions);
    }

    private AttemptView view(SimAttempt a, List<SimAttemptAnswer> given) {
        Map<UUID, SimAttemptAnswer> byItem = new HashMap<>();
        given.forEach(x -> byItem.put(x.getFormItem().getId(), x));
        List<QuestionView> questions = new ArrayList<>();
        int answered = 0;
        for (SimExamFormItem item : a.getForm().getItems()) {
            SimItemVersion v = item.getItemVersion();
            SimAttemptAnswer ans = byItem.get(item.getId());
            if (ans != null && ans.getAnswer() != null) answered++;
            questions.add(new QuestionView(item.getId(), item.getPosition(), v.getType(), v.getStemEn(),
                    registry.learnerView(v.getType(), v.getPayload()), ans == null ? null : ans.getAnswer(), ans != null && ans.isFlagged()));
        }
        return new AttemptView(a.getId(), a.getMode(), a.getStatus(), a.getStartedAt(), a.getDeadlineAt(), LocalDateTime.now(),
                questions.size(), answered, questions);
    }

    private SimAttempt owned(String email, UUID id) {
        Learner learner = learner(email);
        return attempts.findByIdAndLearnerId(id, learner.getId()).orElseThrow(() -> new ResourceNotFoundException("Tentative", "id", id));
    }

    private Learner learner(String email) {
        return learners.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Espace réservé aux apprenants."));
    }

    private SimBlueprint blueprint() {
        return blueprints.findFirstByCertificationCodeAndActiveTrueOrderByVersionDateDesc(QuestionBankService.CERTIFICATION)
                .orElseThrow(() -> new ResourceNotFoundException("Blueprint", "certification", QuestionBankService.CERTIFICATION));
    }

    private void event(SimAttempt a, String type, Map<String, Object> meta) {
        SimAttemptEvent e = new SimAttemptEvent();
        e.setAttempt(a);
        e.setType(type);
        e.setMeta(meta);
        events.save(e);
    }

    private static AccessView access(SimAccessGrant g) {
        return new AccessView(g.getCertification().getCode(), g.getPlanCode().name(), g.getStartsAt(), g.getExpiresAt(),
                g.getExamAttemptsTotal(), g.examAttemptsRemaining());
    }

    private static AttemptSummary summary(SimAttempt a) {
        return new AttemptSummary(a.getId(), a.getMode(), a.getStatus(), a.getStartedAt(), a.getFinishedAt(), a.getForm().getItems().size(),
                a.getScorePercent() == null ? null : a.getScorePercent().doubleValue(), a.getScaledScore(), a.getPassed());
    }

    private static Map<String, Object> bucketMap(Bucket b) {
        return Map.of("code", b.code(), "name", b.name(), "questions", b.questions(), "points", b.points(), "percent", b.percent());
    }

    private static ScoreBucket bucketView(Bucket b) {
        return new ScoreBucket(b.code(), b.name(), b.questions(), b.points(), b.percent());
    }
}
