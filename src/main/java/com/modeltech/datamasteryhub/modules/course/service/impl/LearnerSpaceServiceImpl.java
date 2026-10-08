package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
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

    private final LearnerRepository learnerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseContentAssembler assembler;

    @Value("${app.timezone:Africa/Dakar}")
    private String timezone;

    @Value("${app.brand.name:Model Technologie}")
    private String brandName;

    // =========================================================================
    //  TABLEAU DE BORD
    // =========================================================================

    @Override
    public LearnerPayloads.Dashboard getDashboard(String learnerEmail) {
        Learner learner = requireLearner(learnerEmail);
        LocalDate today = today();
        List<Enrollment> enrollments = distinctByFormation(enrollmentRepository.findAccessibleByLearner(learner.getId()));

        List<UUID> formationIds = enrollments.stream().map(e -> e.getRegistration().getBootcamp().getId()).toList();
        List<CourseLesson> lessons = formationIds.isEmpty() ? List.of()
                : lessonRepository.findAllByBootcampIds(formationIds).stream()
                        .filter(l -> l.getStatus() != LessonStatus.DRAFT).toList();
        Map<UUID, LessonProgress> progress = progressRepository.findAllByLearnerIdAndIsDeletedFalse(learner.getId())
                .stream().collect(Collectors.toMap(p -> p.getLesson().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, List<CourseLesson>> lessonsByFormation = lessons.stream()
                .collect(Collectors.groupingBy(l -> l.getModule().getBootcamp().getId(), LinkedHashMap::new, Collectors.toList()));

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
                    .statusNote(upcoming ? "Démarre le " + DAY.format(e.getAccessStartsAt()) : percent + " %")
                    .status(upcoming ? "UPCOMING" : "IN_PROGRESS")
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
                        .averageQuizScore(null)
                        .certificates(0)
                        .build())
                .resume(resume(enrollments, lessonsByFormation, progress))
                .courses(courses)
                .todos(List.of())
                .certificateReady(null)
                .lives(upcomingLives(lessons))
                .build();
    }

    // =========================================================================
    //  COURS
    // =========================================================================

    @Override
    public LearnerPayloads.LearnerCourse getCourse(String learnerEmail, UUID formationId) {
        Learner learner = requireLearner(learnerEmail);
        Enrollment enrollment = requireAccess(learner, formationId);
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
                .trainerName(null)
                .progress(progress)
                .build();
    }

    // =========================================================================
    //  PROGRESSION
    // =========================================================================

    @Override
    @Transactional
    public void setLessonProgress(String learnerEmail, UUID lessonId, LessonProgressRequest request) {
        Learner learner = requireLearner(learnerEmail);
        CourseLesson lesson = lessonRepository.findByIdAndIsDeletedFalse(lessonId)
                .filter(l -> !l.getModule().isDeleted() && l.getStatus() != LessonStatus.DRAFT)
                .orElseThrow(() -> new com.modeltech.datamasteryhub.exception.ResourceNotFoundException("Leçon", "id", lessonId));
        Bootcamp bootcamp = lesson.getModule().getBootcamp();
        requireAccess(learner, bootcamp.getId());

        if (lesson.getStatus() == LessonStatus.SCHEDULED) {
            throw forbidden("Cette leçon n'est pas encore disponible.");
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
            if (completed && !progress.isCompleted()) throw forbidden("Un quiz se valide en le réussissant.");
            completed = progress.isCompleted();
        }
        if (completed && !progress.isCompleted() && isModuleLocked(learner, lesson)) {
            throw forbidden("Terminez d'abord les leçons du module précédent.");
        }

        progress.setCompleted(completed);
        progress.setPositionSeconds(request.getPositionSeconds());
        progressRepository.save(progress);
    }

    // =========================================================================
    //  ACCÈS
    // =========================================================================

    private Enrollment requireAccess(Learner learner, UUID formationId) {
        Enrollment enrollment = enrollmentRepository.findAccessibleByLearner(learner.getId()).stream()
                .filter(e -> e.getRegistration().getBootcamp().getId().equals(formationId))
                .findFirst()
                .orElseThrow(() -> forbidden("Vous n'avez pas accès à cette formation."));

        CourseConfig config = assembler.configOf(enrollment.getRegistration().getBootcamp());
        LocalDate today = today();
        if (enrollment.getAccessStartsAt() != null && today.isBefore(enrollment.getAccessStartsAt())) {
            throw forbidden("Votre accès s'ouvre le " + DAY.format(enrollment.getAccessStartsAt()) + ".");
        }
        if (CourseConfig.ACCESS_12_MONTHS.equals(config.getAccessDuration())) {
            LocalDate reference = enrollment.getAccessEndsAt() != null
                    ? enrollment.getAccessEndsAt() : enrollment.getAccessStartsAt();
            if (reference != null && today.isAfter(reference.plusMonths(12))) {
                throw forbidden("Votre accès à cette formation a expiré.");
            }
        }
        return enrollment;
    }

    /** Déblocage séquentiel : un module n'est ouvert que si toutes les leçons publiées du précédent sont terminées. */
    private boolean isModuleLocked(Learner learner, CourseLesson target) {
        Bootcamp bootcamp = target.getModule().getBootcamp();
        if (!assembler.configOf(bootcamp).isSequentialUnlock()) return false;

        Set<UUID> completed = progressRepository.findAllByLearnerIdAndIsDeletedFalse(learner.getId()).stream()
                .filter(LessonProgress::isCompleted).map(p -> p.getLesson().getId()).collect(Collectors.toSet());
        Map<UUID, List<CourseLesson>> byModule = lessonRepository.findAllByBootcampIds(List.of(bootcamp.getId())).stream()
                .filter(l -> l.getStatus() != LessonStatus.DRAFT)
                .collect(Collectors.groupingBy(l -> l.getModule().getId(), LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<UUID, List<CourseLesson>> module : byModule.entrySet()) {
            if (module.getKey().equals(target.getModule().getId())) return false;
            if (!module.getValue().stream().allMatch(l -> completed.contains(l.getId()))) return true;
        }
        return false;
    }

    // =========================================================================
    //  CALCULS DU TABLEAU DE BORD
    // =========================================================================

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
                if (e.getAccessStartsAt() != null && today().isBefore(e.getAccessStartsAt())) continue;
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

    private Learner requireLearner(String email) {
        return learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(email)
                .orElseThrow(() -> forbidden("Espace réservé aux apprenants."));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(timezone));
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
