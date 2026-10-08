package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.modules.course.dto.CourseContentPayload;
import com.modeltech.datamasteryhub.modules.course.service.CourseContentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Programme d'une formation. Réponses en JSON brut (sans enveloppe {@code ApiResponse}) :
 * c'est le contrat déjà codé côté front (courseService.ts).
 */
@RestController
@RequestMapping("/api/v1/admin/formations/{formationId}/content")
@RequiredArgsConstructor
@Tag(name = "Admin - Programme des formations")
public class AdminCourseContentController {

    private final CourseContentService courseContentService;

    @GetMapping
    public CourseContentPayload get(@PathVariable UUID formationId, Authentication authentication) {
        return courseContentService.getAdminContent(formationId, authentication.getName(), roles(authentication));
    }

    @PutMapping
    public CourseContentPayload save(@PathVariable UUID formationId,
                                     @RequestBody CourseContentPayload payload,
                                     Authentication authentication) {
        return courseContentService.saveAdminContent(formationId, payload, authentication.getName(), roles(authentication));
    }

    private List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
