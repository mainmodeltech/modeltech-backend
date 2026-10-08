package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.dto.LearnerPayloads;
import com.modeltech.datamasteryhub.modules.course.dto.LessonProgressRequest;
import com.modeltech.datamasteryhub.modules.course.service.EvaluationService;
import com.modeltech.datamasteryhub.modules.course.service.LearnerSpaceService;
import com.modeltech.datamasteryhub.modules.course.service.FinalProjectService;
import com.modeltech.datamasteryhub.modules.course.service.QuizAttemptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Espace apprenant (rôle LEARNER). JSON brut : contrat codé côté front (courseService.ts).
 * Chaque requête est limitée aux accès de l'apprenant connecté.
 */
@RestController
@RequestMapping("/api/v1/learner")
@RequiredArgsConstructor
@Tag(name = "Apprenant")
public class LearnerController {

    private final LearnerSpaceService learnerSpaceService;
    private final EvaluationService evaluationService;
    private final QuizAttemptService quizAttemptService;
    private final FinalProjectService finalProjectService;
    private final com.modeltech.datamasteryhub.modules.course.service.CertificateService certificateService;
    private final com.modeltech.datamasteryhub.modules.course.service.MessagingService messagingService;

    @GetMapping("/dashboard")
    public LearnerPayloads.Dashboard dashboard(Authentication authentication) {
        return learnerSpaceService.getDashboard(authentication.getName());
    }

    @GetMapping("/formations/{formationId}/course")
    public LearnerPayloads.LearnerCourse course(@PathVariable UUID formationId, Authentication authentication) {
        return learnerSpaceService.getCourse(authentication.getName(), formationId);
    }

    @PutMapping("/lessons/{lessonId}/progress")
    public ResponseEntity<Void> setProgress(@PathVariable UUID lessonId,
                                            @Valid @RequestBody LessonProgressRequest request,
                                            Authentication authentication) {
        learnerSpaceService.setLessonProgress(authentication.getName(), lessonId, request);
        return ResponseEntity.noContent().build();
    }

    // ── Questions à l'équipe pédagogique ─────────────────────────────

    @PostMapping("/lessons/{lessonId}/questions")
    public ResponseEntity<com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads.LearnerQuestion> ask(
            @PathVariable UUID lessonId,
            @Valid @RequestBody com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads.AskRequest request,
            Authentication authentication) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(messagingService.ask(authentication.getName(), lessonId, request.getQuestion()));
    }

    @GetMapping("/lessons/{lessonId}/questions")
    public java.util.List<com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads.LearnerQuestion> myQuestions(
            @PathVariable UUID lessonId, Authentication authentication) {
        return messagingService.myQuestions(authentication.getName(), lessonId);
    }

    // ── Certificats ──────────────────────────────────────────────────

    @GetMapping("/certificates")
    public java.util.List<com.modeltech.datamasteryhub.modules.course.dto.CertificatePayloads.LearnerCertificate> certificates(
            Authentication authentication) {
        return certificateService.findForLearner(authentication.getName());
    }

    // ── Évaluations ──────────────────────────────────────────────────

    @GetMapping("/formations/{formationId}/evaluations")
    public EvaluationPayloads.EvaluationsOverview evaluations(@PathVariable UUID formationId, Authentication authentication) {
        return evaluationService.getOverview(authentication.getName(), formationId);
    }

    /** Démarre (ou reprend) une tentative ; {@code quizId} = identifiant de la leçon QUIZ. */
    @PostMapping("/quizzes/{quizId}/attempts")
    public EvaluationPayloads.QuizAttemptStart startAttempt(@PathVariable UUID quizId, Authentication authentication) {
        return quizAttemptService.start(authentication.getName(), quizId);
    }

    @PostMapping("/quiz-attempts/{attemptId}/submit")
    public EvaluationPayloads.QuizAttemptResult submitAttempt(@PathVariable UUID attemptId,
                                                              @RequestBody EvaluationPayloads.QuizSubmission submission,
                                                              Authentication authentication) {
        return quizAttemptService.submit(authentication.getName(), attemptId, submission);
    }

    @PostMapping(value = "/formations/{formationId}/project/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationPayloads.ProjectOverview uploadProjectFile(@PathVariable UUID formationId,
                                                                @RequestPart("file") MultipartFile file,
                                                                Authentication authentication) {
        return finalProjectService.uploadFile(authentication.getName(), formationId, file);
    }

    @DeleteMapping("/formations/{formationId}/project/files/{fileId}")
    public EvaluationPayloads.ProjectOverview deleteProjectFile(@PathVariable UUID formationId, @PathVariable UUID fileId,
                                                                Authentication authentication) {
        return finalProjectService.deleteFile(authentication.getName(), formationId, fileId);
    }
}
