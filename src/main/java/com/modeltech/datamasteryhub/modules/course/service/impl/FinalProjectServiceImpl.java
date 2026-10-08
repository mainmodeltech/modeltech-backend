package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseProject;
import com.modeltech.datamasteryhub.modules.course.entity.ProjectFeedback;
import com.modeltech.datamasteryhub.modules.course.entity.ProjectFile;
import com.modeltech.datamasteryhub.modules.course.entity.ProjectSubmission;
import com.modeltech.datamasteryhub.modules.course.enums.ProjectStatus;
import com.modeltech.datamasteryhub.modules.course.repository.CourseProjectRepository;
import com.modeltech.datamasteryhub.modules.course.repository.ProjectFeedbackRepository;
import com.modeltech.datamasteryhub.modules.course.repository.ProjectFileRepository;
import com.modeltech.datamasteryhub.modules.course.repository.ProjectSubmissionRepository;
import com.modeltech.datamasteryhub.modules.course.service.CourseAccessPolicy;
import com.modeltech.datamasteryhub.modules.course.service.LearnerAccess;
import com.modeltech.datamasteryhub.modules.course.service.FinalProjectService;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FinalProjectServiceImpl implements FinalProjectService {

    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final int MAX_FILES = 10;
    private static final int DOWNLOAD_LINK_MINUTES = 15;

    private final BootcampRepository bootcampRepository;
    private final BootcampSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseProjectRepository projectRepository;
    private final ProjectSubmissionRepository submissionRepository;
    private final ProjectFileRepository fileRepository;
    private final ProjectFeedbackRepository feedbackRepository;
    private final AdminUserRepository adminUserRepository;
    private final StorageService storageService;
    private final LearnerAccess access;
    private final CourseAccessPolicy accessPolicy;

    // =========================================================================
    //  APPRENANT
    // =========================================================================

    @Override
    public EvaluationPayloads.ProjectOverview getLearnerProject(String learnerEmail, UUID formationId) {
        Learner learner = access.requireLearner(learnerEmail);
        access.requireAccess(learner, formationId);
        CourseProject project = projectRepository.findByBootcampId(formationId).filter(p -> !p.isDeleted()).orElse(null);
        return project == null ? null : overview(project, submissionRepository.findByLearnerIdAndBootcampId(learner.getId(), formationId).orElse(null));
    }

    @Override
    @Transactional
    public EvaluationPayloads.ProjectOverview uploadFile(String learnerEmail, UUID formationId, MultipartFile file) {
        Learner learner = access.requireLearner(learnerEmail);
        access.requireAccess(learner, formationId);
        CourseProject project = requireProject(formationId);

        ProjectSubmission submission = submissionRepository.findByLearnerIdAndBootcampId(learner.getId(), formationId)
                .orElseGet(() -> {
                    ProjectSubmission created = new ProjectSubmission();
                    created.setLearner(learner);
                    created.setBootcamp(project.getBootcamp());
                    return created;
                });
        if (submission.getStatus() == ProjectStatus.VALIDATED) {
            throw conflict("Votre projet est validé : il n'est plus modifiable.");
        }
        if (submission.getId() != null && fileRepository
                .findAllBySubmissionIdAndIsDeletedFalseOrderByCreatedAtAsc(submission.getId()).size() >= MAX_FILES) {
            throw conflict("10 fichiers maximum : supprimez-en un avant d'en ajouter.");
        }

        StorageService.UploadResult stored = storageService.uploadDocument(
                file, "projects/" + formationId, Set.copyOf(project.getAcceptedExtensions()),
                project.getMaxSizeMb() * 1024L * 1024L);

        submission.setStatus(ProjectStatus.SUBMITTED);
        submission = submissionRepository.save(submission);

        ProjectFile saved = new ProjectFile();
        saved.setSubmission(submission);
        saved.setName(cleanName(file.getOriginalFilename()));
        saved.setSizeBytes(file.getSize());
        saved.setObjectKey(stored.objectKey());
        fileRepository.save(saved);
        log.info("Projet final : fichier déposé par {} pour la formation {}", learnerEmail, formationId);
        return overview(project, submission);
    }

    @Override
    @Transactional
    public EvaluationPayloads.ProjectOverview deleteFile(String learnerEmail, UUID formationId, UUID fileId) {
        Learner learner = access.requireLearner(learnerEmail);
        access.requireAccess(learner, formationId);
        CourseProject project = requireProject(formationId);
        ProjectSubmission submission = submissionRepository.findByLearnerIdAndBootcampId(learner.getId(), formationId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichier", "id", fileId));
        if (submission.getStatus() == ProjectStatus.VALIDATED) {
            throw conflict("Votre projet est validé : il n'est plus modifiable.");
        }
        ProjectFile file = fileRepository.findByIdAndSubmissionIdAndIsDeletedFalse(fileId, submission.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Fichier", "id", fileId));

        file.setDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        file.setDeletedBy(learnerEmail);
        fileRepository.save(file);
        storageService.delete(file.getObjectKey());

        if (fileRepository.findAllBySubmissionIdAndIsDeletedFalseOrderByCreatedAtAsc(submission.getId()).isEmpty()) {
            submission.setStatus(ProjectStatus.NOT_STARTED);
            submissionRepository.save(submission);
        }
        return overview(project, submission);
    }

    // =========================================================================
    //  BACK-OFFICE : CONSIGNE
    // =========================================================================

    @Override
    public EvaluationPayloads.ProjectConfig getConfig(UUID formationId, String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = requireEditable(formationId, actorEmail, actorRoles);
        return projectRepository.findByBootcampId(bootcamp.getId()).filter(p -> !p.isDeleted())
                .map(this::toConfig).orElseGet(() -> EvaluationPayloads.ProjectConfig.builder().build());
    }

    @Override
    @Transactional
    public EvaluationPayloads.ProjectConfig saveConfig(UUID formationId, EvaluationPayloads.ProjectConfig config,
                                                       String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = requireEditable(formationId, actorEmail, actorRoles);
        if (config.getBrief() == null || config.getBrief().isBlank()) throw bad("La consigne du projet est obligatoire.");
        if (config.getBrief().length() > 10_000) throw bad("Consigne trop longue (10 000 caractères maximum).");
        if (config.getDeadlineLabel() != null && config.getDeadlineLabel().length() > 100) {
            throw bad("L'échéance ne peut pas dépasser 100 caractères.");
        }
        List<String> extensions = normalizeExtensions(config.getAcceptedExtensions());
        int maxMb = config.getMaxSizeMb() == null ? 0 : config.getMaxSizeMb();
        if (maxMb < 1 || maxMb > 50) throw bad("La taille maximale doit être comprise entre 1 et 50 Mo.");

        CourseProject project = projectRepository.findByBootcampId(bootcamp.getId()).orElseGet(() -> {
            CourseProject created = new CourseProject();
            created.setBootcamp(bootcamp);
            return created;
        });
        project.setDeleted(false);
        project.setDeletedAt(null);
        project.setDeletedBy(null);
        project.setBrief(config.getBrief().trim());
        project.setDeadlineLabel(config.getDeadlineLabel() == null || config.getDeadlineLabel().isBlank()
                ? null : config.getDeadlineLabel().trim());
        project.setAcceptedExtensions(extensions);
        project.setMaxSizeMb(maxMb);
        return toConfig(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void deleteConfig(UUID formationId, String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = requireEditable(formationId, actorEmail, actorRoles);
        CourseProject project = projectRepository.findByBootcampId(bootcamp.getId()).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Projet final", "formation", formationId));
        project.setDeleted(true);
        project.setDeletedAt(LocalDateTime.now());
        project.setDeletedBy(actorEmail);
        projectRepository.save(project);
    }

    // =========================================================================
    //  BACK-OFFICE : CORRECTION
    // =========================================================================

    @Override
    @Transactional
    public EvaluationPayloads.ProjectOverview review(UUID sessionId, UUID learnerId, EvaluationPayloads.ProjectReview review,
                                                     String actorEmail, Collection<String> actorRoles) {
        if (review.getStatus() != ProjectStatus.VALIDATED && review.getStatus() != ProjectStatus.CHANGES_REQUESTED) {
            throw bad("Le statut doit être VALIDATED ou CHANGES_REQUESTED.");
        }
        boolean hasMessage = review.getMessage() != null && !review.getMessage().isBlank();
        if (review.getStatus() == ProjectStatus.CHANGES_REQUESTED && !hasMessage) {
            throw bad("Indiquez à l'apprenant les corrections attendues.");
        }
        if (hasMessage && review.getMessage().length() > 5000) throw bad("Message trop long (5 000 caractères maximum).");

        Enrolled enrolled = requireEnrolled(sessionId, learnerId);
        CourseProject project = requireProject(enrolled.bootcamp().getId());
        ProjectSubmission submission = submissionRepository
                .findByLearnerIdAndBootcampId(learnerId, enrolled.bootcamp().getId())
                .filter(s -> s.getStatus() != ProjectStatus.NOT_STARTED)
                .orElseThrow(() -> conflict("Cet apprenant n'a encore rien rendu."));

        submission.setStatus(review.getStatus());
        submissionRepository.save(submission);
        if (hasMessage) {
            ProjectFeedback feedback = new ProjectFeedback();
            feedback.setSubmission(submission);
            feedback.setAuthorName(adminUserRepository.findByEmailAndIsDeletedFalse(actorEmail)
                    .map(a -> a.getFullName()).orElse(actorEmail));
            feedback.setAuthorRole(roleLabel(actorRoles));
            feedback.setMessage(review.getMessage().trim());
            feedbackRepository.save(feedback);
        }
        log.info("Projet final de l'apprenant {} : {} par {}", learnerId, review.getStatus(), actorEmail);
        return overview(project, submission);
    }

    @Override
    public List<EvaluationPayloads.ProjectFileLink> files(UUID sessionId, UUID learnerId) {
        Enrolled enrolled = requireEnrolled(sessionId, learnerId);
        return submissionRepository.findByLearnerIdAndBootcampId(learnerId, enrolled.bootcamp().getId())
                .map(s -> fileRepository.findAllBySubmissionIdAndIsDeletedFalseOrderByCreatedAtAsc(s.getId()).stream()
                        .map(f -> EvaluationPayloads.ProjectFileLink.builder()
                                .id(f.getId().toString())
                                .name(f.getName())
                                .sizeLabel(sizeLabel(f.getSizeBytes()))
                                .downloadUrl(storageService.presignedGetUrl(f.getObjectKey(), DOWNLOAD_LINK_MINUTES))
                                .uploadedAt(f.getCreatedAt())
                                .build())
                        .toList())
                .orElse(List.of());
    }

    // =========================================================================
    //  PRIVÉ
    // =========================================================================

    private record Enrolled(BootcampSession session, Bootcamp bootcamp) {}

    private Enrolled requireEnrolled(UUID sessionId, UUID learnerId) {
        BootcampSession session = sessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));
        boolean enrolled = enrollmentRepository.findActiveBySession(sessionId).stream()
                .anyMatch(e -> e.getLearner().getId().equals(learnerId));
        if (!enrolled) throw new ResourceNotFoundException("Apprenant", "id", learnerId);
        return new Enrolled(session, session.getBootcamp());
    }

    private CourseProject requireProject(UUID formationId) {
        return projectRepository.findByBootcampId(formationId).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Projet final", "formation", formationId));
    }

    private Bootcamp requireEditable(UUID formationId, String actorEmail, Collection<String> actorRoles) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndIsDeletedFalse(formationId)
                .orElseThrow(() -> new ResourceNotFoundException("Formation", "id", formationId));
        accessPolicy.requireEditable(bootcamp, actorEmail, actorRoles);
        return bootcamp;
    }

    private EvaluationPayloads.ProjectOverview overview(CourseProject project, ProjectSubmission submission) {
        List<EvaluationPayloads.ProjectFileItem> files = List.of();
        List<EvaluationPayloads.ProjectFeedbackItem> feedback = List.of();
        if (submission != null && submission.getId() != null) {
            files = fileRepository.findAllBySubmissionIdAndIsDeletedFalseOrderByCreatedAtAsc(submission.getId()).stream()
                    .map(f -> EvaluationPayloads.ProjectFileItem.builder()
                            .id(f.getId().toString()).name(f.getName())
                            .sizeLabel(sizeLabel(f.getSizeBytes())).uploadedAt(f.getCreatedAt()).build())
                    .toList();
            feedback = feedbackRepository.findAllBySubmissionIdAndIsDeletedFalseOrderByCreatedAtAsc(submission.getId()).stream()
                    .map(f -> EvaluationPayloads.ProjectFeedbackItem.builder()
                            .id(f.getId().toString()).authorName(f.getAuthorName()).authorRole(f.getAuthorRole())
                            .message(f.getMessage()).createdAt(f.getCreatedAt()).build())
                    .toList();
        }
        return EvaluationPayloads.ProjectOverview.builder()
                .status(submission != null ? submission.getStatus() : ProjectStatus.NOT_STARTED)
                .brief(project.getBrief())
                .deadlineLabel(project.getDeadlineLabel())
                .acceptedExtensions(project.getAcceptedExtensions())
                .maxSizeMb(project.getMaxSizeMb())
                .files(files)
                .feedback(feedback)
                .build();
    }

    private EvaluationPayloads.ProjectConfig toConfig(CourseProject p) {
        return EvaluationPayloads.ProjectConfig.builder()
                .brief(p.getBrief()).deadlineLabel(p.getDeadlineLabel())
                .acceptedExtensions(p.getAcceptedExtensions()).maxSizeMb(p.getMaxSizeMb()).build();
    }

    private List<String> normalizeExtensions(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw bad("Indiquez au moins une extension de fichier acceptée.");
        List<String> normalized = raw.stream()
                .map(e -> e == null ? "" : e.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""))
                .distinct().toList();
        if (normalized.size() > 20) throw bad("20 extensions maximum.");
        for (String e : normalized) {
            if (!EXTENSION.matcher(e).matches()) throw bad("Extension invalide : « " + e + " ».");
        }
        return normalized;
    }

    /** Nom affiché : sans chemin, sans caractères de contrôle, 255 caractères maximum. */
    private String cleanName(String original) {
        String name = original == null ? "fichier" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) name = "fichier";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String sizeLabel(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024.0)) + " Ko";
        return String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0));
    }

    private String roleLabel(Collection<String> roles) {
        if (roles.contains(RoleNames.TRAINER)) return "Formateur";
        if (roles.contains(RoleNames.PARTNER)) return "Partenaire";
        return "Équipe pédagogique";
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
