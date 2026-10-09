package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.dto.CourseContentPayload;
import com.modeltech.datamasteryhub.modules.course.dto.LearnerPayloads;
import com.modeltech.datamasteryhub.modules.course.dto.LessonProgressRequest;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.LessonProgress;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonProgressRepository;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentAssembler;
import com.modeltech.datamasteryhub.modules.course.service.LearnerAccess;
import com.modeltech.datamasteryhub.modules.course.service.LearnerSpaceService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LearnerSpaceServiceImpl implements LearnerSpaceService {

    private static final Pattern LESSON_NUMBER = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LIVES_HORIZON_DAYS = 14;
    private static final int MAX_LIVES = 5;

    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseContentAssembler assembler;
    private final LearnerAccess access;
    private final com.modeltech.datamasteryhub.modules.course.service.CertificateService certificateService;
    private final com.modeltech.datamasteryhub.modules.course.repository.CertificateRepository certificateRepository;
    private final com.modeltech.datamasteryhub.modules.course.repository.QuizAttemptRepository quizAttemptRepository;
    private final com.modeltech.datamasteryhub.modules.course.repository.CourseProjectRepository courseProjectRepository;
    private final com.modeltech.datamasteryhub.modules.course.repository.ProjectSubmissionRepository projectSubmissionRepository;
    private final com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository paymentRepository;

    @Value("${app.timezone:Africa/Dakar}")
    private String timezone;

    @Value("${app.brand.name:Model Technologie}")
    private String brandName;

    // =========================================================================
    //  TABLEAU DE BORD
    // =========================================================================

    @Override
    public LearnerPayloads.Dashboard getDashboard(String learnerEmail) {
        Learner learner = access.requireLearner(learnerEmail);
        LocalDate today = access.today();
        List<Enrollment> enrollments = distinctByFormation(enrollmentRepository.findAccessibleByLearner(learner.getId()));

        List<UUID> formationIds = enrollments.stream().map(e -> e.getRegistration().getBootcamp().getId()).toList();
        List<CourseLesson> lessons = formationIds.isEmpty() ? List.of()
                : lessonRepository.findAllByBootcampIds(formationIds).stream()
                        .filter(l -> l.getStatus() != LessonStatus.DRAFT).toList();
        Map<UUID, LessonProgress> progress = progressRepository.findAllByLearnerIdAndIsDeletedFalse(learner.getId())
                .stream().collect(Collectors.toMap(p -> p.getLesson().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, List<CourseLesson>> lessonsByFormation = lessons.stream()
                .collect(Collectors.groupingBy(l -> l.getModule().getBootcamp().getId(), LinkedHashMap::new, Collectors.toList()));

        List<com.modeltech.datamasteryhub.modules.course.entity.Certificate> certificates = certificateRepository
                .findAllByLearnerIdAndIsDeletedFalseOrderByIssuedAtDesc(learner.getId()).stream()
                .filter(com.modeltech.datamasteryhub.modules.course.entity.Certificate::isValid).toList();
        Set<UUID> certifiedFormations = certificates.stream().map(c -> c.getBootcamp().getId()).collect(Collectors.toSet());

        List<LearnerPayloads.EnrollmentSummary> courses = new ArrayList<>();
        for (Enrollment e : enrollments) {
            Bootcamp b = e.getRegistration().getBootcamp();
            List<CourseLesson> courseLessons = lessonsByFormation.getOrDefault(b.getId(), List.of());
            int percent = percentDone(courseLessons, progress);
            boolean upcoming = e.getAccessStartsAt() != null && today.isBefore(e.getAccessStartsAt());
            courses.add(LearnerPayloads.EnrollmentSummary.builder()
                    .formationId(b.getId().toString())
                    .title(b.getTitle())
                    .providerLabel(providerLabel(e))
                    .progressPercent(percent)
                    .statusNote(certifiedFormations.contains(b.getId()) ? percent + " % · certificat disponible"
                            : upcoming ? "Démarre le " + DAY.format(e.getAccessStartsAt()) : percent + " %")
                    .status(certifiedFormations.contains(b.getId()) ? "CERTIFIED" : upcoming ? "UPCOMING" : "IN_PROGRESS")
                    .build());
        }

        int done = (int) lessons.stream().filter(l -> isDone(l, progress)).count();
        double seconds = lessons.stream()
                .filter(l -> l.getType() == LessonType.VIDEO && isDone(l, progress) && l.getDurationSeconds() != null)
                .mapToLong(CourseLesson::getDurationSeconds).sum();

        return LearnerPayloads.Dashboard.builder()
                .firstName(learner.getFirstName())
                .streakDays(streak(progress.values(), today))
                .stats(LearnerPayloads.Stats.builder()
                        .hoursWatched(Math.round(seconds / 360.0) / 10.0)
                        .lessonsDone(done)
                        .lessonsTotal(lessons.size())
                        .averageQuizScore(averageQuizScore(learner))
                        .certificates(certificates.size())
                        .build())
                .resume(resume(enrollments, lessonsByFormation, progress))
                .courses(courses)
                .todos(todos(learner, enrollments, lessonsByFormation, progress, today))
                .certificateReady(certificates.stream()
                        .filter(c -> c.getIssuedAt().isAfter(LocalDateTime.now().minusDays(14))).findFirst()
                        .map(c -> LearnerPayloads.CertificateReady.builder().title(c.getFormationTitle()).build())
                        .orElse(null))
                .lives(upcomingLives(lessons))
                .build();
    }

    // =========================================================================
    //  COURS
    // =========================================================================

    @Override
    public LearnerPayloads.LearnerCourse getCourse(String learnerEmail, UUID formationId) {
        Learner learner = access.requireLearner(learnerEmail);
        Enrollment enrollment = access.requireAccess(learner, formationId);
        Bootcamp bootcamp = enrollment.getRegistration().getBootcamp();

        CourseContentPayload content = assembler.assemble(bootcamp, true);
        Set<UUID> lessonIds = content.getModules().stream()
                .flatMap(m -> m.getLessons().stream()).map(l -> UUID.fromString(l.getId()))
                .collect(Collectors.toSet());
        List<LearnerPayloads.LessonProgressItem> progress = progressRepository
                .findAllByLearnerIdAndIsDeletedFalse(learner.getId()).stream()
                .filter(p -> lessonIds.contains(p.getLesson().getId()))
                .map(p -> LearnerPayloads.LessonProgressItem.builder()
                        .lessonId(p.getLesson().getId().toString())
                        .completed(p.isCompleted())
                        .positionSeconds(p.getPositionSeconds())
                        .build())
                .toList();

        String cohort = enrollment.getRegistration().getSessionName();
        return LearnerPayloads.LearnerCourse.builder()
                .content(content)
                .cohortLabel(cohort)
                .trainerName(enrollment.getSession() != null && enrollment.getSession().getTrainer() != null
                        ? enrollment.getSession().getTrainer().getFullName() : null)
                .progress(progress)
                .build();
    }

    // =========================================================================
    //  CALENDRIER ET RESSOURCES
    // =========================================================================

    @Override
    public List<LearnerPayloads.CalendarItem> getCalendar(String learnerEmail, LocalDate from, LocalDate to) {
        Learner learner = access.requireLearner(learnerEmail);
        LocalDate start = from != null ? from : access.today();
        LocalDate end = to != null ? to : start.plusDays(60);
        if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit précéder la date de fin.");
        if (start.plusDays(366).isBefore(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La période ne peut pas dépasser 366 jours.");

        List<Enrollment> enrollments = distinctByFormation(enrollmentRepository.findAccessibleByLearner(learner.getId()));
        List<UUID> formationIds = enrollments.stream().map(e -> e.getRegistration().getBootcamp().getId()).toList();
        if (formationIds.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        LocalDateTime lower = start.atStartOfDay();
        LocalDateTime upper = end.plusDays(1).atStartOfDay();
        return lessonRepository.findAllByBootcampIds(formationIds).stream()
                .filter(l -> l.getStatus() != LessonStatus.DRAFT && l.getType() == LessonType.LIVE && l.getLiveAt() != null)
                .filter(l -> !l.getLiveAt().isBefore(lower) && l.getLiveAt().isBefore(upper))
                .sorted(Comparator.comparing(CourseLesson::getLiveAt))
                .map(l -> LearnerPayloads.CalendarItem.builder()
                        .id(l.getId().toString())
                        .formationId(l.getModule().getBootcamp().getId().toString())
                        .formationTitle(l.getModule().getBootcamp().getTitle())
                        .title(l.getTitle())
                        .startsAt(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(l.getLiveAt()))
                        .timeLabel(String.format("%02dh%02d", l.getLiveAt().getHour(), l.getLiveAt().getMinute()))
                        .place("En ligne")
                        .joinUrl(l.getLiveUrl())
                        .past(l.getLiveAt().isBefore(now))
                        .build())
                .toList();
    }

    @Override
    public List<LearnerPayloads.ResourceEntry> getResources(String learnerEmail) {
        Learner learner = access.requireLearner(learnerEmail);
        List<LearnerPayloads.ResourceEntry> library = new ArrayList<>();
        for (Enrollment e : distinctByFormation(enrollmentRepository.findAccessibleByLearner(learner.getId()))) {
            Bootcamp bootcamp = e.getRegistration().getBootcamp();
            try {
                access.requireAccess(learner, bootcamp.getId());   // accès ouvert et non expiré
            } catch (ResponseStatusException notOpen) {
                continue;
            }
            for (CourseContentPayload.ModuleItem module : assembler.assemble(bootcamp, true).getModules()) {
                for (CourseContentPayload.LessonItem lesson : module.getLessons()) {
                    for (CourseContentPayload.ResourceItem r : lesson.getResources()) {
                        library.add(LearnerPayloads.ResourceEntry.builder()
                                .id(r.getId()).name(r.getName()).fileType(r.getFileType())
                                .sizeLabel(r.getSizeLabel()).note(r.getNote()).url(r.getUrl())
                                .locked(r.getUrl() == null)
                                .formationId(bootcamp.getId().toString()).formationTitle(bootcamp.getTitle())
                                .moduleTitle(module.getTitle())
                                .lessonId(lesson.getId()).lessonTitle(lesson.getTitle())
                                .build());
                    }
                }
            }
        }
        return library;
    }

    // =========================================================================
    //  PROGRESSION
    // =========================================================================

    @Override
    @Transactional
    public void setLessonProgress(String learnerEmail, UUID lessonId, LessonProgressRequest request) {
        Learner learner = access.requireLearner(learnerEmail);
        CourseLesson lesson = lessonRepository.findByIdAndIsDeletedFalse(lessonId)
                .filter(l -> !l.getModule().isDeleted() && l.getStatus() != LessonStatus.DRAFT)
                .orElseThrow(() -> new com.modeltech.datamasteryhub.exception.ResourceNotFoundException("Leçon", "id", lessonId));
        Bootcamp bootcamp = lesson.getModule().getBootcamp();
        access.requireAccess(learner, bootcamp.getId());

        if (lesson.getStatus() == LessonStatus.SCHEDULED) {
            throw access.forbidden("Cette leçon n'est pas encore disponible.");
        }

        LessonProgress progress = progressRepository.findByLearnerIdAndLessonId(learner.getId(), lessonId)
                .orElseGet(() -> {
                    LessonProgress created = new LessonProgress();
                    created.setLearner(learner);
                    created.setLesson(lesson);
                    return created;
                });

        // Un quiz se valide en le réussissant (évaluations), jamais sur simple déclaration du client
        boolean completed = request.getCompleted();
        if (lesson.getType() == LessonType.QUIZ) {
            if (completed && !progress.isCompleted()) throw access.forbidden("Un quiz se valide en le réussissant.");
            completed = progress.isCompleted();
        }
        if (completed && !progress.isCompleted() && access.isModuleLocked(learner, lesson)) {
            throw access.forbidden("Terminez d'abord les leçons du module précédent.");
        }

        progress.setCompleted(completed);
        progress.setPositionSeconds(request.getPositionSeconds());
        progressRepository.save(progress);
        if (completed) {
            certificateService.issueIfEligible(learner.getId(), bootcamp.getId());
        }
    }

    // =========================================================================
    //  CALCULS DU TABLEAU DE BORD
    // =========================================================================

    /** Moyenne des meilleurs scores par quiz (une seule note par quiz), ou null sans tentative rendue. */
    private Integer averageQuizScore(Learner learner) {
        Map<UUID, Integer> best = new HashMap<>();
        quizAttemptRepository.findAllByLearnerIdAndSubmittedAtNotNull(learner.getId()).stream()
                .filter(a -> a.getScore() != null)
                .forEach(a -> best.merge(a.getLesson().getId(), a.getScore(), Math::max));
        if (best.isEmpty()) return null;
        return (int) Math.round(best.values().stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /** À faire : échéances à régler, quiz à passer, projet final à rendre ou à corriger (6 au plus, urgents d'abord). */
    private List<LearnerPayloads.TodoItem> todos(Learner learner, List<Enrollment> enrollments,
                                                 Map<UUID, List<CourseLesson>> lessonsByFormation,
                                                 Map<UUID, LessonProgress> progress, LocalDate today) {
        List<LearnerPayloads.TodoItem> urgent = new ArrayList<>();
        List<LearnerPayloads.TodoItem> normal = new ArrayList<>();

        paymentRepository.findAllByLearner(learner.getId()).stream()
                .filter(p -> p.getStatus() == com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus.PENDING)
                .forEach(p -> {
                    boolean late = p.getDueDate() != null && p.getDueDate().isBefore(today);
                    urgent.add(LearnerPayloads.TodoItem.builder()
                            .id("pay-" + p.getId())
                            .title("Régler l'échéance " + p.getInstallmentNumber() + "/" + p.getInstallmentCount())
                            .subtitle(p.getRegistration().getBootcampTitle()
                                    + (p.getDueDate() != null ? " · avant le " + DAY.format(p.getDueDate()) : ""))
                            .badge(late ? "En retard" : "À régler")
                            .tone("warning")
                            .build());
                });

        for (Enrollment e : enrollments) {
            if (e.getAccessStartsAt() != null && today.isBefore(e.getAccessStartsAt())) continue;
            Bootcamp b = e.getRegistration().getBootcamp();
            lessonsByFormation.getOrDefault(b.getId(), List.of()).stream()
                    .filter(l -> l.getType() == LessonType.QUIZ && l.getStatus() == LessonStatus.PUBLISHED && !isDone(l, progress))
                    .limit(2)
                    .forEach(l -> normal.add(LearnerPayloads.TodoItem.builder()
                            .id("quiz-" + l.getId()).title(l.getTitle()).subtitle(b.getTitle())
                            .badge("Quiz").tone("info").build()));

            if (courseProjectRepository.findByBootcampId(b.getId()).isPresent()) {
                var status = projectSubmissionRepository.findByLearnerIdAndBootcampId(learner.getId(), b.getId())
                        .map(s -> s.getStatus()).orElse(com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus.NOT_STARTED);
                switch (status) {
                    case CHANGES_REQUESTED -> urgent.add(LearnerPayloads.TodoItem.builder()
                            .id("project-" + b.getId()).title("Corriger votre projet final").subtitle(b.getTitle())
                            .badge("À corriger").tone("warning").build());
                    case NOT_STARTED -> normal.add(LearnerPayloads.TodoItem.builder()
                            .id("project-" + b.getId()).title("Rendre votre projet final").subtitle(b.getTitle())
                            .badge("Projet").tone("info").build());
                    default -> { }
                }
            }
        }

        List<LearnerPayloads.TodoItem> all = new ArrayList<>(urgent);
        all.addAll(normal);
        return all.size() > 6 ? new ArrayList<>(all.subList(0, 6)) : all;
    }

    private LearnerPayloads.ResumeInfo resume(List<Enrollment> enrollments,
                                              Map<UUID, List<CourseLesson>> lessonsByFormation,
                                              Map<UUID, LessonProgress> progress) {
        // Dernière leçon commencée et non terminée ; à défaut, première leçon à faire
        Optional<LessonProgress> lastStarted = progress.values().stream()
                .filter(p -> !p.isCompleted() && p.getLesson().getStatus() == LessonStatus.PUBLISHED)
                .filter(p -> lessonsByFormation.values().stream().flatMap(List::stream)
                        .anyMatch(l -> l.getId().equals(p.getLesson().getId())))
                .max(Comparator.comparing(p -> p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt()));

        CourseLesson lesson = null;
        int position = 0;
        if (lastStarted.isPresent()) {
            lesson = lastStarted.get().getLesson();
            position = lastStarted.get().getPositionSeconds();
        } else {
            for (Enrollment e : enrollments) {
                if (e.getAccessStartsAt() != null && access.today().isBefore(e.getAccessStartsAt())) continue;
                lesson = lessonsByFormation.getOrDefault(e.getRegistration().getBootcamp().getId(), List.of()).stream()
                        .filter(l -> l.getStatus() == LessonStatus.PUBLISHED && !isDone(l, progress))
                        .findFirst().orElse(null);
                if (lesson != null) break;
            }
        }
        if (lesson == null) return null;

        Bootcamp bootcamp = lesson.getModule().getBootcamp();
        List<CourseLesson> courseLessons = lessonsByFormation.getOrDefault(bootcamp.getId(), List.of());
        List<UUID> moduleOrder = courseLessons.stream().map(l -> l.getModule().getId()).distinct().toList();
        Matcher number = LESSON_NUMBER.matcher(lesson.getTitle());
        return LearnerPayloads.ResumeInfo.builder()
                .formationId(bootcamp.getId().toString())
                .lessonId(lesson.getId().toString())
                .courseTitle(bootcamp.getTitle())
                .moduleIndex(moduleOrder.indexOf(lesson.getModule().getId()) + 1)
                .moduleCount(moduleOrder.size())
                .lessonTitle(lesson.getTitle())
                .lessonLabel(number.find() ? number.group(1) : "")
                .durationSeconds(lesson.getDurationSeconds() != null ? lesson.getDurationSeconds() : 0)
                .positionSeconds(position)
                .percent(percentDone(courseLessons, progress))
                .build();
    }

    private List<LearnerPayloads.LiveItem> upcomingLives(List<CourseLesson> lessons) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        return lessons.stream()
                .filter(l -> l.getType() == LessonType.LIVE && l.getLiveAt() != null)
                .filter(l -> !l.getLiveAt().isBefore(now) && l.getLiveAt().isBefore(now.plusDays(LIVES_HORIZON_DAYS)))
                .sorted(Comparator.comparing(CourseLesson::getLiveAt))
                .limit(MAX_LIVES)
                .map(l -> LearnerPayloads.LiveItem.builder()
                        .id(l.getId().toString())
                        .startsAt(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(l.getLiveAt()))
                        .title(l.getTitle())
                        .timeLabel(String.format("%02dh%02d", l.getLiveAt().getHour(), l.getLiveAt().getMinute()))
                        .place("En ligne")
                        .joinUrl(l.getLiveUrl())
                        .build())
                .toList();
    }

    /** Jours consécutifs (jusqu'à aujourd'hui ou hier) avec au moins une leçon travaillée ; null si aucun. */
    private Integer streak(Collection<LessonProgress> progress, LocalDate today) {
        Set<LocalDate> days = progress.stream()
                .map(p -> p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt())
                .filter(Objects::nonNull).map(LocalDateTime::toLocalDate).collect(Collectors.toSet());
        LocalDate day = days.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (days.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count == 0 ? null : count;
    }

    private int percentDone(List<CourseLesson> courseLessons, Map<UUID, LessonProgress> progress) {
        if (courseLessons.isEmpty()) return 0;
        long done = courseLessons.stream().filter(l -> isDone(l, progress)).count();
        return (int) Math.round(done * 100.0 / courseLessons.size());
    }

    private boolean isDone(CourseLesson lesson, Map<UUID, LessonProgress> progress) {
        LessonProgress p = progress.get(lesson.getId());
        return p != null && p.isCompleted();
    }

    private String providerLabel(Enrollment e) {
        Bootcamp b = e.getRegistration().getBootcamp();
        String provider = b.getPartner() != null ? b.getPartner().getName() : brandName;
        String session = e.getRegistration().getSessionName();
        return session != null && !session.isBlank() ? provider + " · " + session : provider;
    }

    /** Une formation suivie dans plusieurs sessions n'apparaît qu'une fois (l'accès le plus récent). */
    private List<Enrollment> distinctByFormation(List<Enrollment> enrollments) {
        Map<UUID, Enrollment> byFormation = new LinkedHashMap<>();
        enrollments.forEach(e -> byFormation.putIfAbsent(e.getRegistration().getBootcamp().getId(), e));
        return new ArrayList<>(byFormation.values());
    }
}
