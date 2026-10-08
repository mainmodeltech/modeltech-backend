package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.modules.course.dto.CourseContentPayload;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.CourseModule;
import com.modeltech.datamasteryhub.modules.course.entity.LessonResource;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.repository.CourseConfigRepository;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.CourseModuleRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonResourceRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Construit le programme d'une formation (vue éditeur ou vue apprenant). */
@Component
@RequiredArgsConstructor
public class CourseContentAssembler {

    private final CourseConfigRepository configRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final LessonResourceRepository resourceRepository;

    // Règles appliquées à une formation qui n'a pas encore de réglages enregistrés (modifiables à l'enregistrement).
    @Value("${app.course.default-sequential-unlock:true}")
    private boolean defaultSequentialUnlock;
    @Value("${app.course.default-access-duration:12_MONTHS}")
    private String defaultAccessDuration;
    @Value("${app.course.default-lessons-completed-percent:80}")
    private int defaultLessonsPercent;
    @Value("${app.course.default-quiz-pass-percent:70}")
    private int defaultQuizPercent;
    @Value("${app.course.default-live-presence-percent:75}")
    private int defaultLivePercent;
    @Value("${app.course.default-final-project-validated:true}")
    private boolean defaultFinalProject;
    @Value("${app.course.default-certificate-template:Standard}")
    private String defaultTemplate;

    /** Réglages par défaut (non persistés) d'une formation sans configuration. */
    public CourseConfig defaultConfig(Bootcamp bootcamp) {
        CourseConfig c = new CourseConfig();
        c.setBootcamp(bootcamp);
        c.setSequentialUnlock(defaultSequentialUnlock);
        c.setAccessDuration(defaultAccessDuration);
        c.setLessonsCompletedPercent(defaultLessonsPercent);
        c.setQuizPassPercent(defaultQuizPercent);
        c.setLivePresencePercent(defaultLivePercent);
        c.setFinalProjectValidated(defaultFinalProject);
        c.setCertificateTemplate(defaultTemplate);
        return c;
    }

    public CourseConfig configOf(Bootcamp bootcamp) {
        return configRepository.findByBootcampId(bootcamp.getId()).orElseGet(() -> defaultConfig(bootcamp));
    }

    /**
     * @param learnerView true : brouillons masqués, contenus non encore publiés et ressources
     *                    verrouillées sans leur lien (le serveur ne livre jamais ce que l'apprenant ne peut pas ouvrir)
     */
    public CourseContentPayload assemble(Bootcamp bootcamp, boolean learnerView) {
        CourseConfig config = configOf(bootcamp);
        List<CourseModule> modules = moduleRepository
                .findAllByBootcampIdAndIsDeletedFalseOrderByPositionAsc(bootcamp.getId());
        List<CourseLesson> lessons = lessonRepository.findAllByBootcampIds(List.of(bootcamp.getId()));
        Map<UUID, List<LessonResource>> resourcesByLesson = lessons.isEmpty() ? Map.of()
                : resourceRepository.findAllByLessonIds(lessons.stream().map(CourseLesson::getId).toList()).stream()
                        .collect(Collectors.groupingBy(r -> r.getLesson().getId()));
        Map<UUID, List<CourseLesson>> lessonsByModule = lessons.stream()
                .collect(Collectors.groupingBy(l -> l.getModule().getId(), LinkedHashMap::new, Collectors.toList()));

        List<CourseContentPayload.ModuleItem> moduleItems = new ArrayList<>();
        int moduleOrder = 0;
        for (CourseModule m : modules) {
            List<CourseContentPayload.LessonItem> lessonItems = new ArrayList<>();
            int lessonOrder = 0;
            for (CourseLesson l : lessonsByModule.getOrDefault(m.getId(), List.of())) {
                if (learnerView && l.getStatus() == LessonStatus.DRAFT) continue;
                lessonItems.add(toLessonItem(l, ++lessonOrder, resourcesByLesson.getOrDefault(l.getId(), List.of()), learnerView));
            }
            moduleItems.add(CourseContentPayload.ModuleItem.builder()
                    .id(m.getId().toString())
                    .order(++moduleOrder)
                    .title(m.getTitle())
                    .lessons(lessonItems)
                    .build());
        }

        return CourseContentPayload.builder()
                .formationId(bootcamp.getId().toString())
                .title(bootcamp.getTitle())
                .updatedAt(config.getContentUpdatedAt())
                .settings(CourseContentPayload.Settings.builder()
                        .sequentialUnlock(config.isSequentialUnlock())
                        .accessDuration(config.getAccessDuration())
                        .build())
                .certificateRules(CourseContentPayload.CertificateRules.builder()
                        .lessonsCompletedPercent(config.getLessonsCompletedPercent())
                        .quizPassPercent(config.getQuizPassPercent())
                        .livePresencePercent(config.getLivePresencePercent())
                        .finalProjectValidated(config.isFinalProjectValidated())
                        .template(config.getCertificateTemplate())
                        .build())
                .modules(moduleItems)
                .build();
    }

    private CourseContentPayload.LessonItem toLessonItem(CourseLesson l, int order,
                                                         List<LessonResource> resources, boolean learnerView) {
        boolean published = l.getStatus() == LessonStatus.PUBLISHED;
        boolean hideMedia = learnerView && !published;
        boolean keepLiveLink = !learnerView || l.getType() == LessonType.LIVE;
        CourseContentPayload.Quiz quiz = l.getType() == LessonType.QUIZ && l.getQuizQuestionCount() != null
                ? CourseContentPayload.Quiz.builder()
                        .questionCount(l.getQuizQuestionCount())
                        .passThreshold(l.getQuizPassThreshold())
                        .maxAttempts(l.getQuizMaxAttempts())
                        .build()
                : null;
        return CourseContentPayload.LessonItem.builder()
                .id(l.getId().toString())
                .order(order)
                .title(l.getTitle())
                .subtitle(l.getSubtitle())
                .type(l.getType())
                .status(l.getStatus())
                .durationSeconds(l.getDurationSeconds())
                .videoProviderId(hideMedia ? null : l.getVideoProviderId())
                .videoUrl(hideMedia ? null : l.getVideoUrl())
                .description(l.getDescription())
                .liveAt(l.getLiveAt() != null ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(l.getLiveAt()) : null)
                .liveUrl(keepLiveLink ? l.getLiveUrl() : null)
                .quiz(quiz)
                .resources(resources.stream().map(r -> toResourceItem(r, learnerView, published)).toList())
                .build();
    }

    private CourseContentPayload.ResourceItem toResourceItem(LessonResource r, boolean learnerView, boolean published) {
        // Une ressource verrouillée par un quiz n'expose son lien qu'une fois le quiz réussi (évaluations)
        boolean withhold = learnerView && (!published || r.isLockedUntilQuiz());
        return CourseContentPayload.ResourceItem.builder()
                .id(r.getId().toString())
                .name(r.getName())
                .fileType(r.getFileType())
                .sizeLabel(r.getSizeLabel())
                .note(r.getNote())
                .url(withhold ? null : r.getUrl())
                .lockedUntilQuiz(r.isLockedUntilQuiz())
                .build();
    }
}
