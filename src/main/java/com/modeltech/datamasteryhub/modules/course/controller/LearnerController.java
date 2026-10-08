package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.modules.course.dto.LearnerPayloads;
import com.modeltech.datamasteryhub.modules.course.dto.LessonProgressRequest;
import com.modeltech.datamasteryhub.modules.course.service.LearnerSpaceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}
