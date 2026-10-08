package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.modules.course.dto.EvaluationPayloads;
import com.modeltech.datamasteryhub.modules.course.service.EvaluationService;
import com.modeltech.datamasteryhub.modules.course.service.FinalProjectService;
import com.modeltech.datamasteryhub.modules.course.service.QuizAuthoringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Évaluations côté back-office. JSON brut (contrat front {@code evaluation.type.ts}).
 * <ul>
 *   <li>{@code /admin/sessions/**} : suivi, appel, correction du projet (SUPER_ADMIN, ADMIN, TRAINER) ;</li>
 *   <li>{@code /admin/lessons/{id}/quiz} et {@code /admin/formations/{id}/project} : contenu pédagogique
 *       (SUPER_ADMIN, ADMIN, EDITOR, et PARTNER pour ses formations).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Évaluations")
public class AdminEvaluationController {

    private final EvaluationService evaluationService;
    private final QuizAuthoringService quizAuthoringService;
    private final FinalProjectService finalProjectService;

    // ── Suivi de session ─────────────────────────────────────────────

    @GetMapping("/sessions/{sessionId}/tracking")
    public EvaluationPayloads.SessionTracking tracking(@PathVariable UUID sessionId, Authentication authentication) {
        return evaluationService.getSessionTracking(sessionId, authentication.getName(), roles(authentication));
    }

    @PutMapping("/sessions/{sessionId}/lives/{liveId}/attendance")
    public EvaluationPayloads.SessionLive attendance(@PathVariable UUID sessionId, @PathVariable UUID liveId,
                                                     @RequestBody EvaluationPayloads.AttendanceUpdate update,
                                                     Authentication authentication) {
        return evaluationService.saveAttendance(sessionId, liveId, update, authentication.getName(), roles(authentication));
    }

    @GetMapping("/sessions/{sessionId}/learners/{learnerId}/project/files")
    public List<EvaluationPayloads.ProjectFileLink> projectFiles(@PathVariable UUID sessionId, @PathVariable UUID learnerId,
                                                                 Authentication authentication) {
        return finalProjectService.files(sessionId, learnerId, authentication.getName(), roles(authentication));
    }

    @PostMapping("/sessions/{sessionId}/learners/{learnerId}/project/review")
    public EvaluationPayloads.ProjectOverview reviewProject(@PathVariable UUID sessionId, @PathVariable UUID learnerId,
                                                            @RequestBody EvaluationPayloads.ProjectReview review,
                                                            Authentication authentication) {
        return finalProjectService.review(sessionId, learnerId, review, authentication.getName(), roles(authentication));
    }

    // ── Banque de questions d'un quiz ────────────────────────────────

    @GetMapping("/lessons/{lessonId}/quiz")
    public EvaluationPayloads.QuizBank getQuiz(@PathVariable UUID lessonId, Authentication authentication) {
        return quizAuthoringService.get(lessonId, authentication.getName(), roles(authentication));
    }

    @PutMapping("/lessons/{lessonId}/quiz")
    public EvaluationPayloads.QuizBank saveQuiz(@PathVariable UUID lessonId,
                                                @RequestBody EvaluationPayloads.QuizBank bank,
                                                Authentication authentication) {
        return quizAuthoringService.save(lessonId, bank, authentication.getName(), roles(authentication));
    }

    // ── Projet final d'une formation ─────────────────────────────────

    @GetMapping("/formations/{formationId}/project")
    public EvaluationPayloads.ProjectConfig getProject(@PathVariable UUID formationId, Authentication authentication) {
        return finalProjectService.getConfig(formationId, authentication.getName(), roles(authentication));
    }

    @PutMapping("/formations/{formationId}/project")
    public EvaluationPayloads.ProjectConfig saveProject(@PathVariable UUID formationId,
                                                        @RequestBody EvaluationPayloads.ProjectConfig config,
                                                        Authentication authentication) {
        return finalProjectService.saveConfig(formationId, config, authentication.getName(), roles(authentication));
    }

    @DeleteMapping("/formations/{formationId}/project")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID formationId, Authentication authentication) {
        finalProjectService.deleteConfig(formationId, authentication.getName(), roles(authentication));
        return ResponseEntity.noContent().build();
    }

    private List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
