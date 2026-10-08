package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.course.dto.CourseContentPayload;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.CourseModule;
import com.modeltech.datamasteryhub.modules.course.entity.LessonResource;
import com.modeltech.datamasteryhub.modules.course.enums.LessonType;
import com.modeltech.datamasteryhub.modules.course.repository.CourseConfigRepository;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.CourseModuleRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonResourceRepository;
import com.modeltech.datamasteryhub.modules.course.service.CourseAccessPolicy;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentAssembler;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CourseContentServiceImpl implements CourseContentService {

    private static final Pattern WEB_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_MODULES = 50;
    private static final int MAX_LESSONS_PER_MODULE = 100;
    private static final int MAX_RESOURCES_PER_LESSON = 30;

    private final BootcampRepository bootcampRepository;
    private final CourseConfigRepository configRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final LessonResourceRepository resourceRepository;
    private final CourseContentAssembler assembler;
    private final CourseAccessPolicy accessPolicy;

    /** Fuseau du site, pour convertir une date de live reçue avec un décalage horaire. */
    @Value("${app.timezone:Africa/Dakar}")
    private String timezone;

    // =========================================================================
    //  LECTURE
    // =========================================================================

    @Override
    public CourseContentPayload getAdminContent(UUID formationId, String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = requireEditable(formationId, actorEmail, actorRoles);
        return assembler.assemble(bootcamp, false);
    }

    // =========================================================================
    //  ÉCRITURE
    // =========================================================================

    @Override
    @Transactional
    public CourseContentPayload saveAdminContent(UUID formationId, CourseContentPayload payload,
                                                 String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = requireEditable(formationId, actorEmail, actorRoles);
        List<CourseContentPayload.ModuleItem> modulePayloads =
                payload.getModules() == null ? List.of() : payload.getModules();
        validateShape(modulePayloads);   // tout est validé avant la moindre écriture

        CourseConfig config = saveConfig(bootcamp, payload);

        Map<UUID, CourseModule> existingModules = moduleRepository
                .findAllByBootcampIdAndIsDeletedFalseOrderByPositionAsc(bootcamp.getId()).stream()
                .collect(Collectors.toMap(CourseModule::getId, Function.identity()));
        List<CourseLesson> existingLessonList = lessonRepository.findAllByBootcampIds(List.of(bootcamp.getId()));
        Map<UUID, CourseLesson> existingLessons = existingLessonList.stream()
                .collect(Collectors.toMap(CourseLesson::getId, Function.identity()));
        Map<UUID, LessonResource> existingResources = existingLessons.isEmpty() ? Map.of()
                : resourceRepository.findAllByLessonIds(existingLessons.keySet()).stream()
                        .collect(Collectors.toMap(LessonResource::getId, Function.identity()));

        Set<UUID> keptModules = new HashSet<>();
        Set<UUID> keptLessons = new HashSet<>();
        Set<UUID> keptResources = new HashSet<>();

        int moduleIndex = 0;
        for (CourseContentPayload.ModuleItem mp : modulePayloads) {
            CourseModule module = Optional.ofNullable(match(mp.getId(), existingModules, keptModules))
                    .orElseGet(() -> newModule(bootcamp));
            module.setTitle(mp.getTitle().trim());
            module.setPosition(++moduleIndex);
            module = moduleRepository.save(module);
            keptModules.add(module.getId());

            int lessonIndex = 0;
            for (CourseContentPayload.LessonItem lp : orEmpty(mp.getLessons())) {
                CourseLesson lesson = Optional.ofNullable(match(lp.getId(), existingLessons, keptLessons))
                        .orElseGet(CourseLesson::new);
                applyLesson(lesson, lp, module, ++lessonIndex);
                lesson = lessonRepository.save(lesson);
                keptLessons.add(lesson.getId());

                int resourceIndex = 0;
                for (CourseContentPayload.ResourceItem rp : orEmpty(lp.getResources())) {
                    LessonResource resource = match(rp.getId(), existingResources, keptResources);
                    if (resource == null) resource = new LessonResource();
                    applyResource(resource, rp, lesson, ++resourceIndex);
                    keptResources.add(resourceRepository.save(resource).getId());
                }
            }
        }

        softDeleteMissing(existingResources, keptResources, resourceRepository::save);
        softDeleteMissing(existingLessons, keptLessons, lessonRepository::save);
        softDeleteMissing(existingModules, keptModules, moduleRepository::save);

        config.setContentUpdatedAt(LocalDateTime.now());
        configRepository.save(config);
        log.info("Programme de la formation {} enregistré par {} : {} module(s), {} leçon(s)",
                bootcamp.getId(), actorEmail, keptModules.size(), keptLessons.size());

        // Les écritures sont visibles par les lectures suivantes de la même transaction
        return assembler.assemble(bootcamp, false);
    }

    // =========================================================================
    //  RÉGLAGES
    // =========================================================================

    private CourseConfig saveConfig(Bootcamp bootcamp, CourseContentPayload payload) {
        CourseConfig config = configRepository.findByBootcampId(bootcamp.getId())
                .orElseGet(() -> assembler.defaultConfig(bootcamp));

        CourseContentPayload.Settings s = payload.getSettings();
        if (s != null) {
            if (s.getSequentialUnlock() != null) config.setSequentialUnlock(s.getSequentialUnlock());
            if (s.getAccessDuration() != null) {
                if (!CourseConfig.ACCESS_12_MONTHS.equals(s.getAccessDuration())
                        && !CourseConfig.ACCESS_LIFETIME.equals(s.getAccessDuration())) {
                    throw bad("accessDuration doit valoir 12_MONTHS ou LIFETIME.");
                }
                config.setAccessDuration(s.getAccessDuration());
            }
        }
        CourseContentPayload.CertificateRules r = payload.getCertificateRules();
        if (r != null) {
            if (r.getLessonsCompletedPercent() != null) config.setLessonsCompletedPercent(percent(r.getLessonsCompletedPercent(), "lessonsCompletedPercent"));
            if (r.getQuizPassPercent() != null) config.setQuizPassPercent(percent(r.getQuizPassPercent(), "quizPassPercent"));
            if (r.getLivePresencePercent() != null) config.setLivePresencePercent(percent(r.getLivePresencePercent(), "livePresencePercent"));
            if (r.getFinalProjectValidated() != null) config.setFinalProjectValidated(r.getFinalProjectValidated());
            if (r.getTemplate() != null && !r.getTemplate().isBlank()) {
                if (r.getTemplate().length() > 255) throw bad("template : 255 caractères maximum.");
                config.setCertificateTemplate(r.getTemplate().trim());
            }
        }
        return configRepository.save(config);
    }

    // =========================================================================
    //  APPLICATION DES ÉLÉMENTS
    // =========================================================================

    private CourseModule newModule(Bootcamp bootcamp) {
        CourseModule m = new CourseModule();
        m.setBootcamp(bootcamp);
        return m;
    }

    private void applyLesson(CourseLesson lesson, CourseContentPayload.LessonItem lp, CourseModule module, int position) {
        lesson.setModule(module);
        lesson.setPosition(position);
        lesson.setTitle(lp.getTitle().trim());
        lesson.setSubtitle(blankToNull(lp.getSubtitle()));
        lesson.setType(lp.getType());
        lesson.setStatus(lp.getStatus());
        lesson.setDurationSeconds(lp.getDurationSeconds());
        lesson.setVideoProviderId(blankToNull(lp.getVideoProviderId()));
        lesson.setVideoUrl(webUrl(lp.getVideoUrl(), "videoUrl"));
        lesson.setDescription(blankToNull(lp.getDescription()));
        lesson.setLiveAt(parseLiveAt(lp.getLiveAt()));
        lesson.setLiveUrl(webUrl(lp.getLiveUrl(), "liveUrl"));

        CourseContentPayload.Quiz q = validatedQuiz(lp);
        lesson.setQuizQuestionCount(q != null ? q.getQuestionCount() : null);
        lesson.setQuizPassThreshold(q != null ? q.getPassThreshold() : null);
        lesson.setQuizMaxAttempts(q != null ? q.getMaxAttempts() : null);
    }

    /** Réglages du quiz d'une leçon QUIZ (obligatoires et bornés) ; null pour les autres types. */
    private CourseContentPayload.Quiz validatedQuiz(CourseContentPayload.LessonItem lp) {
        if (lp.getType() != LessonType.QUIZ) return null;
        CourseContentPayload.Quiz q = lp.getQuiz();
        if (q == null || q.getQuestionCount() == null || q.getQuestionCount() < 1) {
            throw bad("Le quiz « " + lp.getTitle() + " » doit avoir au moins 1 question (quiz.questionCount).");
        }
        if (q.getPassThreshold() == null || q.getPassThreshold() < 1 || q.getPassThreshold() > 100) {
            throw bad("Le seuil de réussite du quiz « " + lp.getTitle() + " » doit être compris entre 1 et 100.");
        }
        if (q.getMaxAttempts() != null && q.getMaxAttempts() < 1) {
            throw bad("Le nombre de tentatives du quiz « " + lp.getTitle() + " » doit être au moins 1 (ou vide = illimité).");
        }
        return q;
    }

    private void applyResource(LessonResource resource, CourseContentPayload.ResourceItem rp, CourseLesson lesson, int position) {
        resource.setLesson(lesson);
        resource.setPosition(position);
        resource.setName(rp.getName().trim());
        resource.setFileType(rp.getFileType().trim().toUpperCase(Locale.ROOT));
        resource.setSizeLabel(blankToNull(rp.getSizeLabel()));
        resource.setNote(blankToNull(rp.getNote()));
        resource.setUrl(webUrl(rp.getUrl(), "url de la ressource « " + rp.getName() + " »"));
        resource.setLockedUntilQuiz(Boolean.TRUE.equals(rp.getLockedUntilQuiz()));
    }

    // =========================================================================
    //  VALIDATION ET OUTILS
    // =========================================================================

    private void validateShape(List<CourseContentPayload.ModuleItem> modules) {
        if (modules.size() > MAX_MODULES) throw bad("50 modules maximum.");
        Set<String> seen = new HashSet<>();
        for (CourseContentPayload.ModuleItem m : modules) {
            requireTitle(m.getTitle(), "module");
            requireUniqueId(m.getId(), seen);
            List<CourseContentPayload.LessonItem> lessons = orEmpty(m.getLessons());
            if (lessons.size() > MAX_LESSONS_PER_MODULE) throw bad("100 leçons maximum par module.");
            for (CourseContentPayload.LessonItem l : lessons) {
                requireTitle(l.getTitle(), "leçon");
                requireUniqueId(l.getId(), seen);
                if (l.getType() == null) throw bad("La leçon « " + l.getTitle() + " » n'a pas de type.");
                if (l.getStatus() == null) throw bad("La leçon « " + l.getTitle() + " » n'a pas de statut.");
                if (l.getDurationSeconds() != null && l.getDurationSeconds() < 0) {
                    throw bad("La durée de la leçon « " + l.getTitle() + " » doit être positive.");
                }
                validatedQuiz(l);
                webUrl(l.getVideoUrl(), "videoUrl");
                webUrl(l.getLiveUrl(), "liveUrl");
                parseLiveAt(l.getLiveAt());
                List<CourseContentPayload.ResourceItem> resources = orEmpty(l.getResources());
                if (resources.size() > MAX_RESOURCES_PER_LESSON) throw bad("30 ressources maximum par leçon.");
                for (CourseContentPayload.ResourceItem r : resources) {
                    if (r.getName() == null || r.getName().isBlank()) throw bad("Une ressource n'a pas de nom.");
                    if (r.getFileType() == null || r.getFileType().isBlank()) {
                        throw bad("La ressource « " + r.getName() + " » n'a pas de type de fichier.");
                    }
                    requireUniqueId(r.getId(), seen);
                    webUrl(r.getUrl(), "url de la ressource « " + r.getName() + " »");
                }
            }
        }
    }

    private void requireTitle(String title, String what) {
        if (title == null || title.isBlank()) throw bad("Un(e) " + what + " n'a pas de titre.");
        if (title.length() > 255) throw bad("Titre trop long (255 caractères maximum) : " + title.substring(0, 40) + "…");
    }

    /** Un identifiant ne peut apparaître qu'une fois dans l'arbre (sinon deux éléments écraseraient le même). */
    private void requireUniqueId(String id, Set<String> seen) {
        if (id != null && !id.isBlank() && !seen.add(id)) throw bad("Identifiant en double dans le programme : " + id);
    }

    /** L'élément existant désigné par {@code id}, s'il existe, appartient à la formation et n'est pas déjà utilisé. */
    private <T> T match(String id, Map<UUID, T> existing, Set<UUID> alreadyUsed) {
        UUID uuid = parseUuid(id);
        if (uuid == null || alreadyUsed.contains(uuid)) return null;
        return existing.get(uuid);
    }

    private UUID parseUuid(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;   // identifiant temporaire de l'éditeur : nouvel élément
        }
    }

    private <T extends com.modeltech.datamasteryhub.common.persistence.BaseEntity> void softDeleteMissing(
            Map<UUID, T> existing, Set<UUID> kept, java.util.function.Consumer<T> save) {
        existing.forEach((id, entity) -> {
            if (kept.contains(id)) return;
            entity.setDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy("system");
            save.accept(entity);
        });
    }

    private LocalDateTime parseLiveAt(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v).atZoneSameInstant(ZoneId.of(timezone)).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // pas de décalage horaire : heure locale du site
        }
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            throw bad("Date de live invalide : " + value);
        }
    }

    private String webUrl(String value, String field) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > 2048 || !WEB_URL.matcher(v).matches()) {
            throw bad("Lien invalide (" + field + ") : une adresse http(s):// est attendue.");
        }
        return v;
    }

    private int percent(int value, String field) {
        if (value < 0 || value > 100) throw bad(field + " doit être compris entre 0 et 100.");
        return value;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Bootcamp requireEditable(UUID formationId, String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndIsDeletedFalse(formationId)
                .orElseThrow(() -> new ResourceNotFoundException("Formation", "id", formationId));
        accessPolicy.requireEditable(bootcamp, actorEmail, actorRoles);
        return bootcamp;
    }
}
