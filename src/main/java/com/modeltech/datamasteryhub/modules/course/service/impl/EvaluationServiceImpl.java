package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.*;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus;
import com.modeltech.datamasteryhub.modules.course.repository.*;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentAssembler;
import com.modeltech.datamasteryhub.modules.course.service.EvaluationService;
import com.modeltech.datamasteryhub.modules.course.service.LearnerAccess;
import com.modeltech.datamasteryhub.modules.course.service.FinalProjectService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.SessionFormat;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EvaluationServiceImpl implements EvaluationService {

    private static final int AT_RISK_DAYS = 7;

    private final BootcampSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final QuizAttemptRepository attemptRepository;
    private final CourseProjectRepository projectRepository;
    private final ProjectSubmissionRepository submissionRepository;
    private final LiveRollCallRepository rollCallRepository;
    private final LiveAttendanceRepository attendanceRepository;
    private final FinalProjectService finalProjectService;
    private final CourseContentAssembler assembler;
    private final LearnerAccess access;

    @Value("${app.brand.name:Model Technologie}")
    private String brandName;

    // =========================================================================
    //  APPRENANT : VUE D'ENSEMBLE
    // =========================================================================

    @Override
    public EvaluationPayloads.EvaluationsOverview getOverview(String learnerEmail, UUID formationId) {
        Learner learner = access.requireLearner(learnerEmail);
        Enrollment enrollment = access.requireAccess(learner, formationId);
        Bootcamp bootcamp = enrollment.getRegistration().getBootcamp();
        CourseConfig config = assembler.configOf(bootcamp);

        List<CourseLesson> lessons = lessonsOf(bootcamp);
        Set<UUID> lessonIds = lessons.stream().map(CourseLesson::getId).collect(Collectors.toSet());
        Set<UUID> completed = progressRepository.findAllByLearnerIdAndIsDeletedFalse(learner.getId()).stream()
                .filter(LessonProgress::isCompleted).map(p -> p.getLesson().getId())
                .filter(lessonIds::contains).collect(Collectors.toSet());
        Map<UUID, List<QuizAttempt>> attempts = attemptRepository
                .findAllByLearnerIdAndSubmittedAtNotNull(learner.getId()).stream()
                .filter(a -> lessonIds.contains(a.getLesson().getId()))
                .collect(Collectors.groupingBy(a -> a.getLesson().getId()));
        Set<UUID> locked = lockedModules(config, lessons, completed);

        List<EvaluationPayloads.QuizSummary> quizzes = new ArrayList<>();
        for (CourseLesson l : lessons) {
            if (l.getType() != LessonType.QUIZ) continue;
            List<QuizAttempt> done = attempts.getOrDefault(l.getId(), List.of());
            quizzes.add(summary(l, done, locked.contains(l.getModule().getId())));
        }

        EvaluationPayloads.ProjectOverview project = finalProjectService.getLearnerProject(learnerEmail, formationId);
        Live live = livesOf(enrollment.getSession(), lessons, Set.of(learner.getId()));
        Facts facts = new Facts(lessons, completed,
                attempts.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(a -> Boolean.TRUE.equals(a.getPassed())))
                        .map(Map.Entry::getKey).collect(Collectors.toSet()),
                live.total(), live.presentCount(learner.getId()),
                project != null && config.isFinalProjectValidated() ? project.getStatus() : null);

        return EvaluationPayloads.EvaluationsOverview.builder()
                .formationId(bootcamp.getId().toString())
                .formationTitle(bootcamp.getTitle())
                .cohortLabel(enrollment.getRegistration().getSessionName())
                .passThreshold(config.getQuizPassPercent())
                .quizzes(quizzes)
                .project(project)
                .conditions(conditions(config, facts))
                .build();
    }

    private EvaluationPayloads.QuizSummary summary(CourseLesson l, List<QuizAttempt> done, boolean moduleLocked) {
        boolean passed = done.stream().anyMatch(a -> Boolean.TRUE.equals(a.getPassed()));
        Integer best = done.stream().map(QuizAttempt::getScore).filter(Objects::nonNull).max(Integer::compare).orElse(null);
        Integer max = l.getQuizMaxAttempts();
        String status;
        if (passed) status = "PASSED";
        else if (moduleLocked || l.getStatus() == LessonStatus.SCHEDULED) status = "LOCKED";
        else if (max != null && done.size() >= max) status = "FAILED";
        else status = done.isEmpty() ? "TO_DO" : "AVAILABLE";
        return EvaluationPayloads.QuizSummary.builder()
                .id(l.getId().toString())
                .lessonId(l.getId().toString())
                .title(l.getTitle())
                .questionCount(l.getQuizQuestionCount() != null ? l.getQuizQuestionCount() : 0)
                .timeLimitMinutes(null)
                .passThreshold(l.getQuizPassThreshold() != null ? l.getQuizPassThreshold() : 0)
                .maxAttempts(max)
                .attemptsUsed(done.size())
                .bestScore(best)
                .status(status)
                .dueLabel(null)
                .optional(false)
                .build();
    }

    // =========================================================================
    //  CONDITIONS DU CERTIFICAT
    // =========================================================================

    private record Facts(List<CourseLesson> lessons, Set<UUID> completedLessons, Set<UUID> passedQuizLessons,
                         int livesTotal, int livesPresent, ProjectStatus requiredProject) {}

    private List<EvaluationPayloads.CertificateCondition> conditions(CourseConfig config, Facts f) {
        List<EvaluationPayloads.CertificateCondition> out = new ArrayList<>();

        int lessonsTotal = f.lessons().size();
        int lessonsDone = (int) f.lessons().stream().filter(l -> f.completedLessons().contains(l.getId())).count();
        int lessonsPercent = percent(lessonsDone, lessonsTotal);
        out.add(condition("LESSONS", "Leçons terminées", lessonsDone + " / " + lessonsTotal, lessonsPercent,
                lessonsTotal == 0 || lessonsPercent >= config.getLessonsCompletedPercent()));

        int quizTotal = (int) f.lessons().stream().filter(l -> l.getType() == LessonType.QUIZ).count();
        int quizPassed = (int) f.lessons().stream()
                .filter(l -> l.getType() == LessonType.QUIZ && f.passedQuizLessons().contains(l.getId())).count();
        out.add(condition("QUIZZES", "Quiz réussis", quizPassed + " / " + quizTotal, percent(quizPassed, quizTotal),
                quizPassed == quizTotal));

        int livePercent = percent(f.livesPresent(), f.livesTotal());
        out.add(condition("LIVES", "Présence aux lives", f.livesPresent() + " / " + f.livesTotal(), livePercent,
                f.livesTotal() == 0 || livePercent >= config.getLivePresencePercent()));

        if (f.requiredProject() != null) {
            boolean validated = f.requiredProject() == ProjectStatus.VALIDATED;
            out.add(condition("PROJECT", "Projet final", projectLabel(f.requiredProject()), validated ? 100 : 0, validated));
        }
        return out;
    }

    private EvaluationPayloads.CertificateCondition condition(String key, String label, String value, int percent, boolean met) {
        return EvaluationPayloads.CertificateCondition.builder()
                .key(key).label(label).valueLabel(value).percent(percent).met(met).build();
    }

    private String projectLabel(ProjectStatus s) {
        return switch (s) {
            case NOT_STARTED -> "Non rendu";
            case SUBMITTED -> "En correction";
            case CHANGES_REQUESTED -> "Corrections demandées";
            case VALIDATED -> "Validé";
        };
    }

    private int percent(int part, int total) {
        return total == 0 ? 100 : (int) Math.round(part * 100.0 / total);
    }

    // =========================================================================
    //  SUIVI DE SESSION (BACK-OFFICE)
    // =========================================================================

    @Override
    public EvaluationPayloads.SessionTracking getSessionTracking(UUID sessionId) {
        BootcampSession session = requireSession(sessionId);
        Bootcamp bootcamp = session.getBootcamp();
        CourseConfig config = assembler.configOf(bootcamp);
        List<Enrollment> enrollments = enrollmentRepository.findActiveBySession(sessionId);
        List<UUID> learnerIds = enrollments.stream().map(e -> e.getLearner().getId()).toList();

        List<CourseLesson> lessons = lessonsOf(bootcamp);
        Set<UUID> lessonIds = lessons.stream().map(CourseLesson::getId).collect(Collectors.toSet());
        boolean hasProject = projectRepository.findByBootcampId(bootcamp.getId()).filter(p -> !p.isDeleted()).isPresent();

        Map<UUID, Set<UUID>> completedBy = new HashMap<>();
        Map<UUID, Set<UUID>> passedQuizBy = new HashMap<>();
        Map<UUID, ProjectStatus> projectBy = new HashMap<>();
        if (!learnerIds.isEmpty()) {
            progressRepository.findAllByLearnerIdInAndIsDeletedFalse(learnerIds).stream()
                    .filter(p -> p.isCompleted() && lessonIds.contains(p.getLesson().getId()))
                    .forEach(p -> completedBy.computeIfAbsent(p.getLearner().getId(), k -> new HashSet<>()).add(p.getLesson().getId()));
            attemptRepository.findAllByLearnerIdInAndSubmittedAtNotNull(learnerIds).stream()
                    .filter(a -> Boolean.TRUE.equals(a.getPassed()) && lessonIds.contains(a.getLesson().getId()))
                    .forEach(a -> passedQuizBy.computeIfAbsent(a.getLearner().getId(), k -> new HashSet<>()).add(a.getLesson().getId()));
            submissionRepository.findAllByBootcampIdAndLearnerIdIn(bootcamp.getId(), learnerIds)
                    .forEach(s -> projectBy.put(s.getLearner().getId(), s.getStatus()));
        }
        Live live = livesOf(session, lessons, new HashSet<>(learnerIds));

        LocalDate today = access.today();
        boolean endingSoon = session.getEndDate() != null && !today.isBefore(session.getEndDate().minusDays(AT_RISK_DAYS));
        List<EvaluationPayloads.TrackedLearner> learners = new ArrayList<>();
        for (Enrollment e : enrollments) {
            UUID id = e.getLearner().getId();
            Set<UUID> completed = completedBy.getOrDefault(id, Set.of());
            Set<UUID> passed = passedQuizBy.getOrDefault(id, Set.of());
            ProjectStatus project = projectBy.getOrDefault(id, ProjectStatus.NOT_STARTED);
            Facts facts = new Facts(lessons, completed, passed, live.total(), live.presentCount(id),
                    hasProject && config.isFinalProjectValidated() ? project : null);
            boolean ready = conditions(config, facts).stream().allMatch(EvaluationPayloads.CertificateCondition::isMet);

            int quizTotal = (int) lessons.stream().filter(l -> l.getType() == LessonType.QUIZ).count();
            learners.add(EvaluationPayloads.TrackedLearner.builder()
                    .learnerId(id.toString())
                    .name(e.getLearner().getFullName())
                    .subtitle(subtitle(e.getRegistration()))
                    .progressPercent(lessons.isEmpty() ? 0
                            : (int) Math.round(lessons.stream().filter(l -> completed.contains(l.getId())).count()
                                    * 100.0 / lessons.size()))
                    .quizPassed((int) lessons.stream().filter(l -> l.getType() == LessonType.QUIZ && passed.contains(l.getId())).count())
                    .quizTotal(quizTotal)
                    .project(project)
                    .certificate(ready ? "READY" : endingSoon ? "AT_RISK" : "PENDING")
                    .build());
        }

        LocalDateTime now = LocalDateTime.now(access.zone());
        List<EvaluationPayloads.SessionLive> lives = lessons.stream()
                .filter(l -> l.getType() == LessonType.LIVE)
                .map(l -> {
                    LiveRollCall call = live.callByLesson().get(l.getId());
                    boolean done = call != null || (l.getLiveAt() != null && l.getLiveAt().isBefore(now));
                    return EvaluationPayloads.SessionLive.builder()
                            .id(l.getId().toString())
                            .title(l.getTitle())
                            .startsAt(l.getLiveAt())
                            .status(done ? "DONE" : "UPCOMING")
                            .presentLearnerIds(call == null ? null : live.presentIds(call.getId()))
                            .build();
                })
                .toList();

        return EvaluationPayloads.SessionTracking.builder()
                .sessionId(session.getId().toString())
                .formationId(bootcamp.getId().toString())
                .formationTitle(bootcamp.getTitle())
                .sessionName(session.getSessionName())
                .deliveredBy(bootcamp.getPartner() != null ? bootcamp.getPartner().getName() : brandName)
                .trainerName(null)
                .startDate(session.getStartDate())
                .endDate(session.getEndDate())
                .formatLabel(formatLabel(session))
                .statusLabel(statusLabel(session.getStatus()))
                .capacity(session.getMaxParticipants() != null ? session.getMaxParticipants() : 0)
                .learners(learners)
                .lives(lives)
                .build();
    }

    @Override
    @Transactional
    public EvaluationPayloads.SessionLive saveAttendance(UUID sessionId, UUID liveId,
                                                         EvaluationPayloads.AttendanceUpdate update, String actorEmail) {
        BootcampSession session = requireSession(sessionId);
        CourseLesson lesson = lessonRepository.findByIdAndIsDeletedFalse(liveId)
                .filter(l -> l.getType() == LessonType.LIVE && l.getStatus() != LessonStatus.DRAFT && !l.getModule().isDeleted()
                        && l.getModule().getBootcamp().getId().equals(session.getBootcamp().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Live", "id", liveId));

        Set<UUID> enrolled = enrollmentRepository.findActiveBySession(sessionId).stream()
                .map(e -> e.getLearner().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> present = new HashSet<>();
        for (String raw : update.getPresentLearnerIds() == null ? List.<String>of() : update.getPresentLearnerIds()) {
            UUID id;
            try {
                id = UUID.fromString(raw);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant d'apprenant invalide : " + raw);
            }
            if (!enrolled.contains(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cet apprenant n'est pas inscrit à la session : " + raw);
            }
            present.add(id);
        }

        LiveRollCall call = rollCallRepository.findBySessionIdAndLessonId(sessionId, liveId).orElseGet(() -> {
            LiveRollCall created = new LiveRollCall();
            created.setSession(session);
            created.setLesson(lesson);
            return created;
        });
        call.setDeleted(false);
        call.setTakenAt(LocalDateTime.now());
        call.setTakenBy(actorEmail);
        call = rollCallRepository.save(call);

        Map<UUID, LiveAttendance> existing = attendanceRepository.findAllByRollCallId(call.getId()).stream()
                .collect(Collectors.toMap(a -> a.getLearner().getId(), a -> a));
        List<Enrollment> enrollments = enrollmentRepository.findActiveBySession(sessionId);
        for (Enrollment e : enrollments) {
            UUID id = e.getLearner().getId();
            LiveAttendance row = existing.get(id);
            if (row == null) {
                row = new LiveAttendance();
                row.setRollCall(call);
                row.setLearner(e.getLearner());
            }
            row.setPresent(present.contains(id));
            attendanceRepository.save(row);
        }
        log.info("Appel du live {} (session {}) enregistré par {} : {}/{} présents",
                liveId, sessionId, actorEmail, present.size(), enrolled.size());

        return EvaluationPayloads.SessionLive.builder()
                .id(lesson.getId().toString())
                .title(lesson.getTitle())
                .startsAt(lesson.getLiveAt())
                .status("DONE")
                .presentLearnerIds(present.stream().map(UUID::toString).sorted().toList())
                .build();
    }

    // =========================================================================
    //  OUTILS
    // =========================================================================

    private BootcampSession requireSession(UUID sessionId) {
        return sessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));
    }

    /** Leçons non brouillon de la formation, dans l'ordre du programme. */
    private List<CourseLesson> lessonsOf(Bootcamp bootcamp) {
        return lessonRepository.findAllByBootcampIds(List.of(bootcamp.getId())).stream()
                .filter(l -> l.getStatus() != LessonStatus.DRAFT).toList();
    }

    /** Modules encore fermés pour l'apprenant (déblocage séquentiel). */
    private Set<UUID> lockedModules(CourseConfig config, List<CourseLesson> lessons, Set<UUID> completed) {
        Set<UUID> locked = new HashSet<>();
        if (!config.isSequentialUnlock()) return locked;
        Map<UUID, List<CourseLesson>> byModule = lessons.stream()
                .collect(Collectors.groupingBy(l -> l.getModule().getId(), LinkedHashMap::new, Collectors.toList()));
        boolean previousComplete = true;
        for (Map.Entry<UUID, List<CourseLesson>> m : byModule.entrySet()) {
            if (!previousComplete) locked.add(m.getKey());
            previousComplete = previousComplete && m.getValue().stream().allMatch(l -> completed.contains(l.getId()));
        }
        return locked;
    }

    /** Appels déjà faits pour une session : par live, et présences des apprenants demandés. */
    private record Live(Map<UUID, LiveRollCall> callByLesson, Map<UUID, List<LiveAttendance>> attendanceByCall, int total) {
        int presentCount(UUID learnerId) {
            return (int) attendanceByCall.values().stream().flatMap(List::stream)
                    .filter(a -> a.isPresent() && a.getLearner().getId().equals(learnerId)).count();
        }

        List<String> presentIds(UUID callId) {
            return attendanceByCall.getOrDefault(callId, List.of()).stream().filter(LiveAttendance::isPresent)
                    .map(a -> a.getLearner().getId().toString()).sorted().toList();
        }
    }

    private Live livesOf(BootcampSession session, List<CourseLesson> lessons, Set<UUID> learnerIds) {
        if (session == null) return new Live(Map.of(), Map.of(), 0);
        Set<UUID> liveLessonIds = lessons.stream().filter(l -> l.getType() == LessonType.LIVE)
                .map(CourseLesson::getId).collect(Collectors.toSet());
        Map<UUID, LiveRollCall> calls = rollCallRepository.findAllBySessionIdAndIsDeletedFalse(session.getId()).stream()
                .filter(c -> liveLessonIds.contains(c.getLesson().getId()))
                .collect(Collectors.toMap(c -> c.getLesson().getId(), c -> c));
        Map<UUID, List<LiveAttendance>> attendance = calls.isEmpty() ? Map.of()
                : attendanceRepository.findAllByRollCallIdIn(calls.values().stream().map(LiveRollCall::getId).toList()).stream()
                        .filter(a -> learnerIds.contains(a.getLearner().getId()))
                        .collect(Collectors.groupingBy(a -> a.getRollCall().getId()));
        // Seuls les lives dont l'appel a été fait comptent dans la présence
        return new Live(calls, attendance, calls.size());
    }

    private String subtitle(Registration r) {
        List<String> parts = new ArrayList<>();
        if (r.getPosition() != null && !r.getPosition().isBlank()) parts.add(r.getPosition().trim());
        if (r.getCompany() != null && !r.getCompany().isBlank()) parts.add(r.getCompany().trim());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private String formatLabel(BootcampSession s) {
        String format = s.getFormat() == null ? "" : switch (s.getFormat()) {
            case PRESENTIEL -> "Présentiel";
            case REMOTE -> "À distance";
            case HYBRID -> "Hybride";
        };
        String location = s.getLocation() != null && !s.getLocation().isBlank() ? " " + s.getLocation().trim() : "";
        return (format + location).trim();
    }

    private String statusLabel(SessionStatus status) {
        if (status == null) return "";
        return switch (status) {
            case DRAFT -> "Brouillon";
            case UPCOMING -> "À venir";
            case OPEN -> "Inscriptions ouvertes";
            case CLOSED -> "Inscriptions closes";
            case IN_PROGRESS -> "En cours";
            case COMPLETED -> "Terminée";
            case CANCELLED -> "Annulée";
        };
    }
}
