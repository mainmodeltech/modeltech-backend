package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.audit.service.FourEyes;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.course.dto.CourseContentPayload;
import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.dto.ProposalPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseContentProposal;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.enums.ProposalStatus;
import com.modeltech.datamasteryhub.modules.course.repository.CourseContentProposalRepository;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentAssembler;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentService;
import com.modeltech.datamasteryhub.modules.course.service.ProgramProposalService;
import com.modeltech.datamasteryhub.modules.course.service.QuizAuthoringService;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.notification.service.ProgramProposalNotice;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProgramProposalServiceImpl implements ProgramProposalService {

    private static final Set<ProposalStatus> ACTIVE =
            EnumSet.of(ProposalStatus.DRAFT, ProposalStatus.SUBMITTED, ProposalStatus.CHANGES_REQUESTED);
    private static final Set<ProposalStatus> EDITABLE = EnumSet.of(ProposalStatus.DRAFT, ProposalStatus.CHANGES_REQUESTED);
    private static final String ENTITY = "ProgramProposal";
    private static final int MAX_COMMENT = 4000;
    private static final String BASE_IDS = "baseIds";

    private final CourseContentProposalRepository proposalRepository;
    private final BootcampRepository bootcampRepository;
    private final BootcampSessionRepository sessionRepository;
    private final AdminUserRepository adminUserRepository;
    private final CourseContentAssembler assembler;
    private final CourseContentService courseContentService;
    private final QuizAuthoringService quizAuthoringService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // =========================================================================
    //  FORMATEUR
    // =========================================================================

    @Override
    public List<ProposalPayloads.ProposableFormation> myFormations(String actorEmail) {
        AdminUser author = adminUserRepository.findByEmailAndIsDeletedFalse(actorEmail).orElse(null);
        Map<UUID, Bootcamp> formations = new LinkedHashMap<>();
        for (BootcampSession s : sessionRepository.findAllByTrainerEmailIgnoreCaseAndIsDeletedFalseOrderByStartDateDesc(actorEmail)) {
            Bootcamp b = s.getBootcamp();
            if (b != null && !b.isDeleted()) formations.putIfAbsent(b.getId(), b);
        }
        return formations.values().stream()
                .sorted(Comparator.comparing(b -> b.getTitle() == null ? "" : b.getTitle(), String.CASE_INSENSITIVE_ORDER))
                .map(b -> ProposalPayloads.ProposableFormation.builder()
                        .formationId(b.getId().toString())
                        .formationTitle(b.getTitle())
                        .proposal(author == null ? null : active(b, author)
                                .or(() -> proposalRepository.findFirstByBootcampIdAndAuthorIdAndIsDeletedFalseOrderByUpdatedAtDesc(b.getId(), author.getId()))
                                .map(p -> ProposalPayloads.ProposalRef.builder()
                                        .id(p.getId().toString()).status(p.getStatus()).updatedAt(p.getUpdatedAt()).build())
                                .orElse(null))
                        .build())
                .toList();
    }

    @Override
    public ProposalPayloads.Proposal getMine(UUID formationId, String actorEmail) {
        Bootcamp bootcamp = requireProposable(formationId, actorEmail);
        AdminUser author = account(actorEmail);
        Optional<CourseContentProposal> current = active(bootcamp, author);
        if (current.isPresent()) return toPayload(current.get(), false, actorEmail);

        // Pas de proposition en cours : copie du programme en ligne, point de départ de l'éditeur
        ProposalPayloads.Live live = liveSnapshot(bootcamp);
        return ProposalPayloads.Proposal.builder()
                .formationId(bootcamp.getId().toString())
                .formationTitle(bootcamp.getTitle())
                .baseContentUpdatedAt(live.getContent().getUpdatedAt())
                .liveContentUpdatedAt(live.getContent().getUpdatedAt())
                .content(live.getContent())
                .quizBanks(live.getQuizBanks())
                .own(true)
                .liveLessonStatuses(lessonStatuses(live))
                .build();
    }

    @Override
    @Transactional
    public ProposalPayloads.Proposal saveMine(UUID formationId, ProposalPayloads.Body body, String actorEmail) {
        Bootcamp bootcamp = requireProposable(formationId, actorEmail);
        AdminUser author = account(actorEmail);
        CourseContentProposal proposal = active(bootcamp, author).orElse(null);
        if (proposal != null && !EDITABLE.contains(proposal.getStatus())) {
            throw conflict("Votre proposition est en cours de relecture : retirez-la pour la modifier.");
        }

        ProposalPayloads.Live live = liveSnapshot(bootcamp);
        ProposalPayloads.Body normalized = normalize(body);
        boolean created = proposal == null;
        // Éléments en ligne connus de la proposition (fixés à sa création) : eux seuls ne peuvent pas être retirés
        Set<String> baseIds = created ? liveIds(live) : baseIds(proposal);
        validateAgainstLive(normalized, live, baseIds);

        if (created) {
            proposal = new CourseContentProposal();
            proposal.setBootcamp(bootcamp);
            proposal.setAuthor(author);
            proposal.setStatus(ProposalStatus.DRAFT);
            proposal.setBaseContentUpdatedAt(live.getContent().getUpdatedAt());
        }
        proposal.setPayload(write(normalized, baseIds));
        proposal = proposalRepository.saveAndFlush(proposal);
        if (created) {
            auditService.record(AuditAction.PROGRAM_PROPOSAL_CREATED, ENTITY, proposal.getId(),
                    "Proposition de modification du programme « " + bootcamp.getTitle() + " » créée");
        }
        log.info("Proposition {} du programme {} enregistrée par {}", proposal.getId(), bootcamp.getId(), actorEmail);
        return toPayload(proposal, false, actorEmail);
    }

    @Override
    @Transactional
    public ProposalPayloads.Proposal submit(UUID formationId, String actorEmail) {
        Bootcamp bootcamp = requireProposable(formationId, actorEmail);
        CourseContentProposal proposal = active(bootcamp, account(actorEmail))
                .orElseThrow(() -> conflict("Aucune proposition à soumettre : enregistrez-la d'abord."));
        if (!EDITABLE.contains(proposal.getStatus())) throw conflict("Cette proposition est déjà soumise.");

        validateAgainstLive(read(proposal), liveSnapshot(bootcamp), baseIds(proposal));
        proposal.setStatus(ProposalStatus.SUBMITTED);
        proposal.setSubmittedAt(LocalDateTime.now());
        proposal = proposalRepository.saveAndFlush(proposal);
        auditService.record(AuditAction.PROGRAM_PROPOSAL_SUBMITTED, ENTITY, proposal.getId(),
                "Proposition de modification du programme « " + bootcamp.getTitle() + " » soumise à la pédagogie");
        notificationService.notifyProgramProposalSubmitted(new ProgramProposalNotice(
                reviewerEmails(actorEmail), bootcamp.getTitle(), nameOf(proposal.getAuthor()), null, "SUBMITTED", null));
        return toPayload(proposal, false, actorEmail);
    }

    @Override
    @Transactional
    public ProposalPayloads.Proposal withdraw(UUID formationId, String actorEmail) {
        Bootcamp bootcamp = requireProposable(formationId, actorEmail);
        CourseContentProposal proposal = active(bootcamp, account(actorEmail))
                .filter(p -> p.getStatus() == ProposalStatus.SUBMITTED)
                .orElseThrow(() -> conflict("Seule une proposition soumise peut être retirée."));
        proposal.setStatus(ProposalStatus.DRAFT);
        proposal.setSubmittedAt(null);
        proposal = proposalRepository.saveAndFlush(proposal);
        auditService.record(AuditAction.PROGRAM_PROPOSAL_WITHDRAWN, ENTITY, proposal.getId(),
                "Proposition de modification du programme « " + bootcamp.getTitle() + " » retirée par son auteur");
        return toPayload(proposal, false, actorEmail);
    }

    // =========================================================================
    //  PÉDAGOGIE
    // =========================================================================

    @Override
    public List<ProposalPayloads.Summary> list(ProposalStatus status, Collection<String> actorAuthorities) {
        requireReviewer(actorAuthorities);
        List<CourseContentProposal> proposals = status == null
                ? proposalRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                : proposalRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(status);
        Map<UUID, LocalDateTime> liveDates = new HashMap<>();
        return proposals.stream().map(p -> {
            LocalDateTime liveAt = liveDates.computeIfAbsent(p.getBootcamp().getId(),
                    id -> assembler.configOf(p.getBootcamp()).getContentUpdatedAt());
            return ProposalPayloads.Summary.builder()
                    .id(p.getId().toString())
                    .formationId(p.getBootcamp().getId().toString())
                    .formationTitle(p.getBootcamp().getTitle())
                    .authorName(nameOf(p.getAuthor()))
                    .status(p.getStatus())
                    .submittedAt(p.getSubmittedAt())
                    .updatedAt(p.getUpdatedAt())
                    .stale(isStale(p, liveAt))
                    .build();
        }).toList();
    }

    @Override
    public long pendingCount(String actorEmail, Collection<String> actorAuthorities) {
        requireReviewer(actorAuthorities);
        return proposalRepository.countToReview(ProposalStatus.SUBMITTED, actorEmail);
    }

    @Override
    public ProposalPayloads.Proposal detail(UUID id, String actorEmail, Collection<String> actorAuthorities) {
        requireReviewer(actorAuthorities);
        return toPayload(proposal(id), true, actorEmail);
    }

    @Override
    @Transactional
    public ProposalPayloads.Proposal approve(UUID id, ProposalPayloads.Decision decision, String actorEmail,
                                             Collection<String> actorAuthorities) {
        CourseContentProposal proposal = forDecision(id, actorEmail, actorAuthorities);
        Bootcamp bootcamp = proposal.getBootcamp();
        boolean publishNew = decision != null && Boolean.TRUE.equals(decision.getPublishNewLessons());

        ProposalPayloads.Body body = read(proposal);
        ProposalPayloads.Live live = liveSnapshot(bootcamp);
        validateAgainstLive(body, live, baseIds(proposal));
        merge(bootcamp, body, live, publishNew, actorEmail, actorAuthorities);   // même transaction : tout ou rien

        decide(proposal, ProposalStatus.APPROVED, comment(decision, false), actorEmail);
        auditService.record(AuditAction.PROGRAM_PROPOSAL_APPROVED, ENTITY, proposal.getId(),
                "Proposition de " + nameOf(proposal.getAuthor()) + " validée : programme « " + bootcamp.getTitle() + " » mis à jour"
                        + (publishNew ? " (nouvelles leçons publiées)" : " (nouvelles leçons en brouillon)"));
        notifyAuthor(proposal);
        log.info("Proposition {} validée par {} : programme {} mis à jour", proposal.getId(), actorEmail, bootcamp.getId());
        return toPayload(proposal, false, actorEmail);
    }

    @Override
    @Transactional
    public ProposalPayloads.Proposal requestChanges(UUID id, ProposalPayloads.Decision decision, String actorEmail,
                                                    Collection<String> actorAuthorities) {
        CourseContentProposal proposal = forDecision(id, actorEmail, actorAuthorities);
        decide(proposal, ProposalStatus.CHANGES_REQUESTED, comment(decision, true), actorEmail);
        auditService.record(AuditAction.PROGRAM_PROPOSAL_CHANGES_REQUESTED, ENTITY, proposal.getId(),
                "Modifications demandées à " + nameOf(proposal.getAuthor()) + " sur le programme « " + proposal.getBootcamp().getTitle() + " »");
        notifyAuthor(proposal);
        return toPayload(proposal, false, actorEmail);
    }

    @Override
    @Transactional
    public ProposalPayloads.Proposal reject(UUID id, ProposalPayloads.Decision decision, String actorEmail,
                                            Collection<String> actorAuthorities) {
        CourseContentProposal proposal = forDecision(id, actorEmail, actorAuthorities);
        decide(proposal, ProposalStatus.REJECTED, comment(decision, true), actorEmail);
        auditService.record(AuditAction.PROGRAM_PROPOSAL_REJECTED, ENTITY, proposal.getId(),
                "Proposition de " + nameOf(proposal.getAuthor()) + " refusée (programme « " + proposal.getBootcamp().getTitle() + " »)");
        notifyAuthor(proposal);
        return toPayload(proposal, false, actorEmail);
    }

    private CourseContentProposal forDecision(UUID id, String actorEmail, Collection<String> actorAuthorities) {
        requireReviewer(actorAuthorities);
        CourseContentProposal proposal = proposal(id);
        if (proposal.getStatus() != ProposalStatus.SUBMITTED) {
            throw conflict("Seule une proposition soumise peut être validée, renvoyée ou refusée.");
        }
        // Séparation des tâches : l'auteur ne décide jamais de sa propre proposition (il peut la retirer)
        if (FourEyes.same(proposal.getAuthor().getEmail(), actorEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous êtes l'auteur de cette proposition : une autre personne de la pédagogie doit la relire.");
        }
        return proposal;
    }

    private void decide(CourseContentProposal proposal, ProposalStatus status, String comment, String actorEmail) {
        proposal.setStatus(status);
        proposal.setReviewer(adminUserRepository.findByEmailAndIsDeletedFalse(actorEmail).orElse(null));
        proposal.setReviewComment(comment);
        proposal.setReviewedAt(LocalDateTime.now());
        proposalRepository.saveAndFlush(proposal);
    }

    private String comment(ProposalPayloads.Decision decision, boolean required) {
        String c = decision == null || decision.getComment() == null ? null : decision.getComment().trim();
        if (c == null || c.isEmpty()) {
            if (required) throw bad("Un commentaire est obligatoire pour expliquer votre décision au formateur.");
            return null;
        }
        if (c.length() > MAX_COMMENT) throw bad("Le commentaire dépasse " + MAX_COMMENT + " caractères.");
        return c;
    }

    private void notifyAuthor(CourseContentProposal p) {
        notificationService.sendProgramProposalDecision(new ProgramProposalNotice(
                List.of(p.getAuthor().getEmail()), p.getBootcamp().getTitle(), nameOf(p.getAuthor()),
                nameOf(p.getReviewer()), p.getStatus().name(), p.getReviewComment()));
    }

    /** Comptes actifs qui peuvent relire (programme + toutes les formations ; la Direction compte), hors auteur. */
    private List<String> reviewerEmails(String authorEmail) {
        List<String> emails = new ArrayList<>();
        for (AdminUser u : adminUserRepository.findAllByActiveTrueAndIsDeletedFalse()) {
            if (FourEyes.same(u.getEmail(), authorEmail)) continue;
            Set<String> permissions = new HashSet<>();
            boolean superAdmin = false;
            for (Role role : u.getRoles()) {
                superAdmin |= RoleNames.SUPER_ADMIN.equals(role.getName());
                permissions.addAll(role.getPermissions());
            }
            if (superAdmin || (permissions.contains(Permission.PROGRAM_MANAGE.name())
                    && permissions.contains(Permission.SCOPE_ALL_FORMATIONS.name()))) {
                emails.add(u.getEmail());
            }
        }
        return emails;
    }

    // =========================================================================
    //  RÈGLES : rien d'existant ne disparaît
    // =========================================================================

    /**
     * Mise en forme à l'enregistrement : réglages et règles du certificat ignorés (ceux en ligne font foi), toute leçon
     * nouvelle en brouillon avec un {@code clientKey} (généré s'il manque).
     */
    private ProposalPayloads.Body normalize(ProposalPayloads.Body body) {
        if (body == null || body.getContent() == null) throw bad("Le programme proposé est vide (content).");
        CourseContentPayload content = body.getContent();
        content.setSettings(null);
        content.setCertificateRules(null);
        content.setUpdatedAt(null);
        content.setFormationId(null);
        content.setTitle(null);
        if (content.getModules() == null) content.setModules(new ArrayList<>());
        for (CourseContentPayload.ModuleItem m : content.getModules()) {
            if (m.getLessons() == null) m.setLessons(new ArrayList<>());
            for (CourseContentPayload.LessonItem l : m.getLessons()) {
                if (l.getResources() == null) l.setResources(new ArrayList<>());
                if (uuid(l.getId()) != null) continue;   // existant (ou inconnu : refusé par la validation)
                // Nouvelle leçon : son identifiant temporaire (« new-… ») est gardé tel quel ; clientKey = lui, à défaut généré
                if (isBlank(l.getClientKey())) l.setClientKey(isBlank(l.getId()) ? "new-" + UUID.randomUUID() : l.getId().trim());
                l.setStatus(LessonStatus.DRAFT);
            }
        }
        if (body.getQuizBanks() == null) body.setQuizBanks(new ArrayList<>());
        return body;
    }

    /**
     * Contrôles à l'enregistrement, à la soumission et à la validation, face au programme en ligne ACTUEL : forme du programme
     * et des quiz ; tout identifiant UUID doit exister en ligne ; tout élément en ligne connu de la proposition à sa création ({@code baseIds}) doit rester présent (ceux ajoutés en ligne depuis sont conservés par la fusion).
     */
    private void validateAgainstLive(ProposalPayloads.Body body, ProposalPayloads.Live live, Set<String> baseIds) {
        CourseContentPayload content = body.getContent();
        if (content == null) throw bad("Le programme proposé est vide (content).");
        courseContentService.validateContent(content);
        Index online = new Index(live);

        Set<String> modules = new HashSet<>();
        Set<String> lessons = new HashSet<>();
        Set<String> resources = new HashSet<>();
        Map<String, CourseContentPayload.LessonItem> quizLessonsByRef = new HashMap<>();
        Set<String> clientKeys = new HashSet<>();
        for (CourseContentPayload.ModuleItem m : orEmpty(content.getModules())) {
            if (known(m.getId(), online.modules, "module « " + m.getTitle() + " »")) modules.add(m.getId().trim());
            for (CourseContentPayload.LessonItem l : orEmpty(m.getLessons())) {
                String ref;
                if (known(l.getId(), online.lessons, "leçon « " + l.getTitle() + " »")) {
                    ref = l.getId().trim();
                    lessons.add(ref);
                    CourseContentPayload.LessonItem current = online.lessons.get(ref);
                    if (l.getStatus() != LessonStatus.DRAFT && l.getStatus() != current.getStatus()) {
                        throw bad("La leçon « " + l.getTitle() + " » ne peut être que dans son statut en ligne ou en brouillon.");
                    }
                    if (current.getType() == LessonType.QUIZ && l.getType() != LessonType.QUIZ) {
                        throw bad("La leçon « " + current.getTitle() + " » est un quiz en ligne : son type ne peut pas être changé.");
                    }
                } else {
                    ref = isBlank(l.getClientKey()) ? null : l.getClientKey().trim();
                    if (ref == null) throw bad("La nouvelle leçon « " + l.getTitle() + " » n'a pas de clientKey.");
                    if (!clientKeys.add(ref) || online.lessons.containsKey(ref)) throw bad("clientKey en double dans la proposition : " + ref);
                    if (l.getStatus() != LessonStatus.DRAFT) {
                        throw bad("La nouvelle leçon « " + l.getTitle() + " » doit être en brouillon.");
                    }
                }
                if (l.getType() == LessonType.QUIZ) quizLessonsByRef.put(ref, l);
                for (CourseContentPayload.ResourceItem r : orEmpty(l.getResources())) {
                    if (known(r.getId(), online.resources, "ressource « " + r.getName() + " »")) resources.add(r.getId().trim());
                }
            }
        }

        // Aucun élément en ligne ne peut être retiré
        for (CourseContentPayload.ModuleItem m : orEmpty(live.getContent().getModules())) {
            if (baseIds.contains(m.getId()) && !modules.contains(m.getId())) {
                throw bad("Le module « " + m.getTitle() + " » existe en ligne et ne peut pas être retiré.");
            }
            for (CourseContentPayload.LessonItem l : orEmpty(m.getLessons())) {
                if (baseIds.contains(l.getId()) && !lessons.contains(l.getId())) {
                    throw bad("La leçon « " + l.getTitle() + " » existe en ligne et ne peut pas être retirée : passez-la en brouillon.");
                }
                for (CourseContentPayload.ResourceItem r : orEmpty(l.getResources())) {
                    if (baseIds.contains(r.getId()) && !resources.contains(r.getId())) {
                        throw bad("La ressource « " + r.getName() + " » de la leçon « " + l.getTitle()
                                + " » existe en ligne et ne peut pas être retirée.");
                    }
                }
            }
        }

        // Banques de questions : une par leçon quiz de la proposition ; les questions en ligne restent
        Map<String, ProposalPayloads.QuizBankDraft> banks = new HashMap<>();
        for (ProposalPayloads.QuizBankDraft bank : orEmpty(body.getQuizBanks())) {
            String ref = bank.getLessonRef() == null ? null : bank.getLessonRef().trim();
            CourseContentPayload.LessonItem lesson = ref == null ? null : quizLessonsByRef.get(ref);
            if (lesson == null) throw bad("Banque de questions sans leçon quiz correspondante dans la proposition : " + bank.getLessonRef());
            if (banks.put(ref, bank) != null) throw bad("Deux banques de questions pour la leçon « " + lesson.getTitle() + " ».");
            try {
                quizAuthoringService.validateQuestions(bank.getQuestions());
            } catch (ResponseStatusException e) {
                throw bad("Quiz « " + lesson.getTitle() + " » : " + e.getReason());
            }
            Map<String, EvaluationPayloads.QuizQuestionEdit> onlineQuestions = online.questionsOf(ref);
            for (EvaluationPayloads.QuizQuestionEdit q : orEmpty(bank.getQuestions())) {
                UUID qid = uuid(q.getId());
                if (qid != null && !onlineQuestions.containsKey(qid.toString())) {
                    throw bad("Question inconnue dans le quiz en ligne « " + lesson.getTitle() + " » : " + q.getId()
                            + ". Rechargez la proposition.");
                }
            }
        }
        for (Map.Entry<String, Map<String, EvaluationPayloads.QuizQuestionEdit>> e : online.questions.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            ProposalPayloads.QuizBankDraft bank = banks.get(e.getKey());
            Set<String> kept = new HashSet<>();
            if (bank != null) orEmpty(bank.getQuestions()).forEach(q -> { if (q.getId() != null) kept.add(q.getId().trim()); });
            for (EvaluationPayloads.QuizQuestionEdit q : e.getValue().values()) {
                if (baseIds.contains(q.getId()) && !kept.contains(q.getId())) {
                    throw bad("La question « " + abbreviate(q.getText()) + " » du quiz « " + online.lessons.get(e.getKey()).getTitle()
                            + " » existe en ligne et ne peut pas être retirée.");
                }
            }
        }
    }

    /**
     * Vrai si {@code id} désigne un élément en ligne. Un identifiant non UUID (ou vide) est celui d'un élément nouveau ; un UUID
     * absent du programme en ligne est refusé (élément supprimé depuis, ou d'une autre formation).
     */
    private boolean known(String id, Map<String, ?> online, String what) {
        UUID uuid = uuid(id);
        if (uuid == null) return false;
        if (!online.containsKey(uuid.toString())) {
            throw bad("Élément inconnu dans le programme en ligne (" + what + ") : " + id + ". Rechargez la proposition.");
        }
        return true;
    }

    // =========================================================================
    //  VALIDATION = FUSION dans le programme en ligne actuel
    // =========================================================================

    /**
     * Part du programme en ligne ACTUEL : les éléments de la proposition sont mis à jour (dans son ordre), les nouveaux ajoutés,
     * et ceux ajoutés en ligne depuis (absents de la proposition) conservés, placés après. Puis les banques de questions, avec la
     * même règle. Écrit par la logique d'enregistrement existante (réglages et règles du certificat inchangés).
     */
    private void merge(Bootcamp bootcamp, ProposalPayloads.Body body, ProposalPayloads.Live live, boolean publishNew,
                       String actorEmail, Collection<String> actorAuthorities) {
        CourseContentPayload proposed = body.getContent();
        Set<String> proposedModules = new HashSet<>();
        Set<String> proposedLessons = new HashSet<>();
        Set<String> proposedResources = new HashSet<>();
        for (CourseContentPayload.ModuleItem m : orEmpty(proposed.getModules())) {
            if (uuid(m.getId()) != null) proposedModules.add(m.getId().trim());
            for (CourseContentPayload.LessonItem l : orEmpty(m.getLessons())) {
                if (uuid(l.getId()) != null) proposedLessons.add(l.getId().trim());
                for (CourseContentPayload.ResourceItem r : orEmpty(l.getResources())) {
                    if (uuid(r.getId()) != null) proposedResources.add(r.getId().trim());
                }
            }
        }
        Index online = new Index(live);

        List<CourseContentPayload.ModuleItem> modules = new ArrayList<>();
        for (CourseContentPayload.ModuleItem pm : orEmpty(proposed.getModules())) {
            List<CourseContentPayload.LessonItem> lessons = new ArrayList<>();
            for (CourseContentPayload.LessonItem pl : orEmpty(pm.getLessons())) {
                CourseContentPayload.LessonItem l = copy(pl);
                if (uuid(pl.getId()) == null) {
                    l.setId(pl.getClientKey().trim());   // identifiant temporaire : créé par l'enregistrement
                    l.setStatus(publishNew ? LessonStatus.PUBLISHED : LessonStatus.DRAFT);
                }
                List<CourseContentPayload.ResourceItem> resources = new ArrayList<>(orEmpty(pl.getResources()));
                CourseContentPayload.LessonItem current = uuid(pl.getId()) == null ? null : online.lessons.get(pl.getId().trim());
                if (current != null) {
                    orEmpty(current.getResources()).stream().filter(r -> !proposedResources.contains(r.getId())).forEach(resources::add);
                }
                l.setResources(resources);
                lessons.add(l);
            }
            CourseContentPayload.ModuleItem current = uuid(pm.getId()) == null ? null : online.modules.get(pm.getId().trim());
            if (current != null) {
                orEmpty(current.getLessons()).stream().filter(l -> !proposedLessons.contains(l.getId()))
                        .map(l -> withoutResources(l, proposedResources)).forEach(lessons::add);
            }
            modules.add(CourseContentPayload.ModuleItem.builder().id(pm.getId()).title(pm.getTitle()).lessons(lessons).build());
        }
        for (CourseContentPayload.ModuleItem lm : orEmpty(live.getContent().getModules())) {
            if (proposedModules.contains(lm.getId())) continue;
            List<CourseContentPayload.LessonItem> lessons = orEmpty(lm.getLessons()).stream()
                    .filter(l -> !proposedLessons.contains(l.getId()))
                    .map(l -> withoutResources(l, proposedResources)).toList();
            modules.add(CourseContentPayload.ModuleItem.builder().id(lm.getId()).title(lm.getTitle()).lessons(new ArrayList<>(lessons)).build());
        }

        CourseContentPayload merged = CourseContentPayload.builder().modules(modules).build();   // réglages nuls : inchangés
        CourseContentPayload saved = courseContentService.saveAdminContent(bootcamp.getId(), merged, actorEmail, actorAuthorities);

        // Référence de leçon (id en ligne ou clientKey) → identifiant définitif : même arbre, même ordre
        Map<String, String> lessonIds = new HashMap<>();
        for (int i = 0; i < modules.size(); i++) {
            List<CourseContentPayload.LessonItem> sent = modules.get(i).getLessons();
            List<CourseContentPayload.LessonItem> written = saved.getModules().get(i).getLessons();
            for (int j = 0; j < sent.size(); j++) lessonIds.put(sent.get(j).getId(), written.get(j).getId());
        }

        for (ProposalPayloads.QuizBankDraft bank : orEmpty(body.getQuizBanks())) {
            String ref = bank.getLessonRef().trim();
            List<EvaluationPayloads.QuizQuestionEdit> questions = new ArrayList<>(orEmpty(bank.getQuestions()));
            Set<String> proposedQuestions = new HashSet<>();
            questions.forEach(q -> { if (q.getId() != null) proposedQuestions.add(q.getId().trim()); });
            online.questionsOf(ref).values().stream().filter(q -> !proposedQuestions.contains(q.getId())).forEach(questions::add);
            EvaluationPayloads.QuizBank write = EvaluationPayloads.QuizBank.builder().questions(questions).build();
            quizAuthoringService.save(UUID.fromString(lessonIds.get(ref)), write, actorEmail, actorAuthorities);
        }
    }

    private CourseContentPayload.LessonItem copy(CourseContentPayload.LessonItem l) {
        return CourseContentPayload.LessonItem.builder()
                .id(l.getId()).title(l.getTitle()).subtitle(l.getSubtitle()).type(l.getType()).status(l.getStatus())
                .durationSeconds(l.getDurationSeconds()).videoProviderId(l.getVideoProviderId()).videoUrl(l.getVideoUrl())
                .description(l.getDescription()).liveAt(l.getLiveAt()).liveUrl(l.getLiveUrl()).quiz(l.getQuiz())
                .resources(new ArrayList<>(orEmpty(l.getResources())))
                .build();
    }

    private CourseContentPayload.LessonItem withoutResources(CourseContentPayload.LessonItem l, Set<String> excluded) {
        CourseContentPayload.LessonItem c = copy(l);
        c.setResources(new ArrayList<>(orEmpty(l.getResources()).stream().filter(r -> !excluded.contains(r.getId())).toList()));
        return c;
    }

    // =========================================================================
    //  LECTURE ET OUTILS
    // =========================================================================

    /** Programme en ligne et banque de chaque leçon quiz. */
    private ProposalPayloads.Live liveSnapshot(Bootcamp bootcamp) {
        CourseContentPayload content = assembler.assemble(bootcamp, false);
        List<ProposalPayloads.QuizBankDraft> banks = new ArrayList<>();
        for (CourseContentPayload.ModuleItem m : orEmpty(content.getModules())) {
            for (CourseContentPayload.LessonItem l : orEmpty(m.getLessons())) {
                if (l.getType() != LessonType.QUIZ) continue;
                banks.add(ProposalPayloads.QuizBankDraft.builder()
                        .lessonRef(l.getId())
                        .questions(new ArrayList<>(quizAuthoringService.questionsOf(UUID.fromString(l.getId()))))
                        .build());
            }
        }
        return ProposalPayloads.Live.builder().content(content).quizBanks(banks).build();
    }

    private ProposalPayloads.Proposal toPayload(CourseContentProposal p, boolean withLive, String actorEmail) {
        Bootcamp bootcamp = p.getBootcamp();
        ProposalPayloads.Live live = liveSnapshot(bootcamp);
        ProposalPayloads.Body body = read(p);
        CourseContentPayload content = body.getContent();
        content.setFormationId(bootcamp.getId().toString());
        content.setTitle(bootcamp.getTitle());
        content.setUpdatedAt(p.getUpdatedAt());
        // Réglages et règles du certificat : ceux en ligne (non modifiables par une proposition)
        content.setSettings(live.getContent().getSettings());
        content.setCertificateRules(live.getContent().getCertificateRules());
        LocalDateTime liveAt = live.getContent().getUpdatedAt();
        return ProposalPayloads.Proposal.builder()
                .id(p.getId().toString())
                .formationId(bootcamp.getId().toString())
                .formationTitle(bootcamp.getTitle())
                .status(p.getStatus())
                .authorName(nameOf(p.getAuthor()))
                .reviewerName(nameOf(p.getReviewer()))
                .reviewComment(p.getReviewComment())
                .baseContentUpdatedAt(p.getBaseContentUpdatedAt())
                .liveContentUpdatedAt(liveAt)
                .stale(isStale(p, liveAt))
                .submittedAt(p.getSubmittedAt())
                .reviewedAt(p.getReviewedAt())
                .updatedAt(p.getUpdatedAt())
                .content(content)
                .quizBanks(body.getQuizBanks())
                .own(FourEyes.same(p.getAuthor().getEmail(), actorEmail))
                .liveLessonStatuses(lessonStatuses(live))
                .live(withLive ? live : null)
                .build();
    }

    /** Statut en ligne de chaque leçon existante : l'auteur peut revenir du brouillon au statut d'origine. */
    private static Map<String, LessonStatus> lessonStatuses(ProposalPayloads.Live live) {
        Map<String, LessonStatus> statuses = new LinkedHashMap<>();
        for (CourseContentPayload.ModuleItem m : orEmpty(live.getContent().getModules())) {
            orEmpty(m.getLessons()).forEach(l -> statuses.put(l.getId(), l.getStatus()));
        }
        return statuses;
    }

    /** Le programme en ligne a changé depuis le début de la proposition (sans objet une fois la décision prise). */
    private boolean isStale(CourseContentProposal p, LocalDateTime liveAt) {
        return ACTIVE.contains(p.getStatus()) && !Objects.equals(p.getBaseContentUpdatedAt(), liveAt);
    }

    /**
     * JSON gardé : {@code {content, quizBanks}} plus {@code baseIds}, les identifiants en ligne à la création de la proposition.
     * Ce qui a été ajouté en ligne depuis n'est pas exigé dans la proposition : la fusion le conserve.
     */
    private Map<String, Object> write(ProposalPayloads.Body body, Set<String> baseIds) {
        Map<String, Object> json = objectMapper.convertValue(body, new TypeReference<Map<String, Object>>() {});
        json.put(BASE_IDS, new ArrayList<>(baseIds));
        return json;
    }

    private Set<String> baseIds(CourseContentProposal p) {
        Object ids = p.getPayload() == null ? null : p.getPayload().get(BASE_IDS);
        Set<String> result = new HashSet<>();
        if (ids instanceof Collection<?> c) c.forEach(id -> result.add(String.valueOf(id)));
        return result;
    }

    /** Identifiants de tous les éléments en ligne (modules, leçons, ressources, questions). */
    private static Set<String> liveIds(ProposalPayloads.Live live) {
        Set<String> ids = new HashSet<>();
        for (CourseContentPayload.ModuleItem m : orEmpty(live.getContent().getModules())) {
            ids.add(m.getId());
            for (CourseContentPayload.LessonItem l : orEmpty(m.getLessons())) {
                ids.add(l.getId());
                orEmpty(l.getResources()).forEach(r -> ids.add(r.getId()));
            }
        }
        orEmpty(live.getQuizBanks()).forEach(b -> orEmpty(b.getQuestions()).forEach(q -> ids.add(q.getId())));
        return ids;
    }

    private ProposalPayloads.Body read(CourseContentProposal p) {
        ProposalPayloads.Body body = objectMapper.convertValue(p.getPayload(), ProposalPayloads.Body.class);
        if (body.getContent() == null) body.setContent(new CourseContentPayload());
        if (body.getQuizBanks() == null) body.setQuizBanks(new ArrayList<>());
        return body;
    }

    private Optional<CourseContentProposal> active(Bootcamp bootcamp, AdminUser author) {
        return proposalRepository.findFirstByBootcampIdAndAuthorIdAndStatusInAndIsDeletedFalse(bootcamp.getId(), author.getId(), ACTIVE);
    }

    private CourseContentProposal proposal(UUID id) {
        return proposalRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proposition", "id", id));
    }

    /** La formation existe et au moins une de ses sessions est confiée à la personne connectée. */
    private Bootcamp requireProposable(UUID formationId, String actorEmail) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndIsDeletedFalse(formationId)
                .orElseThrow(() -> new ResourceNotFoundException("Formation", "id", formationId));
        if (!sessionRepository.existsByBootcampIdAndTrainerEmailIgnoreCaseAndIsDeletedFalse(formationId, actorEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez proposer des modifications que pour les formations dont une session vous est confiée.");
        }
        return bootcamp;
    }

    private void requireReviewer(Collection<String> actorAuthorities) {
        if (!actorAuthorities.contains(Permission.SCOPE_ALL_FORMATIONS.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La relecture des propositions de programme est réservée à la pédagogie (toutes les formations).");
        }
    }

    private AdminUser account(String email) {
        return adminUserRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte du back-office introuvable."));
    }

    private static String nameOf(AdminUser u) {
        if (u == null) return null;
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail();
    }

    private static UUID uuid(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;   // identifiant temporaire : élément nouveau
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= 60 ? t : t.substring(0, 60) + "…";
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** Le programme en ligne indexé par identifiant (modules, leçons, ressources, questions des leçons quiz). */
    private static final class Index {
        final Map<String, CourseContentPayload.ModuleItem> modules = new HashMap<>();
        final Map<String, CourseContentPayload.LessonItem> lessons = new HashMap<>();
        final Map<String, CourseContentPayload.ResourceItem> resources = new HashMap<>();
        /** Leçon quiz → ses questions en ligne (ordre conservé). */
        final Map<String, Map<String, EvaluationPayloads.QuizQuestionEdit>> questions = new HashMap<>();

        Index(ProposalPayloads.Live live) {
            for (CourseContentPayload.ModuleItem m : orEmpty(live.getContent().getModules())) {
                modules.put(m.getId(), m);
                for (CourseContentPayload.LessonItem l : orEmpty(m.getLessons())) {
                    lessons.put(l.getId(), l);
                    orEmpty(l.getResources()).forEach(r -> resources.put(r.getId(), r));
                }
            }
            for (ProposalPayloads.QuizBankDraft bank : orEmpty(live.getQuizBanks())) {
                Map<String, EvaluationPayloads.QuizQuestionEdit> byId = new LinkedHashMap<>();
                orEmpty(bank.getQuestions()).forEach(q -> byId.put(q.getId(), q));
                questions.put(bank.getLessonRef(), byId);
            }
        }

        Map<String, EvaluationPayloads.QuizQuestionEdit> questionsOf(String lessonRef) {
            return questions.getOrDefault(lessonRef, Map.of());
        }
    }
}
