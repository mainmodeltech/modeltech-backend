package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.course.dto.CommunityPayloads;
import com.modeltech.datamasteryhub.modules.course.service.CommunityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Communauté : fil d'entraide par formation (apprenants) et modération (ADMIN / SUPER_ADMIN). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Communauté")
public class CommunityController {

    private final CommunityService communityService;

    // ── Apprenants (JSON brut, comme /learner/**) ────────────────────

    @GetMapping("/api/v1/learner/formations/{formationId}/community")
    public CommunityPayloads.ThreadPage threads(@PathVariable UUID formationId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                Authentication authentication) {
        return communityService.threads(authentication.getName(), formationId, page, size);
    }

    @PostMapping("/api/v1/learner/formations/{formationId}/community")
    public ResponseEntity<CommunityPayloads.Post> post(@PathVariable UUID formationId,
                                                       @Valid @RequestBody CommunityPayloads.CreateRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.post(authentication.getName(), formationId, request));
    }

    @DeleteMapping("/api/v1/learner/community/{postId}")
    public ResponseEntity<Void> deleteOwn(@PathVariable UUID postId, Authentication authentication) {
        communityService.deleteOwn(authentication.getName(), postId);
        return ResponseEntity.noContent().build();
    }

    // ── Modération ───────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/community")
    public ResponseEntity<ApiResponse<List<CommunityPayloads.ModerationPost>>> moderationList(
            @RequestParam UUID formationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<CommunityPayloads.ModerationPost> posts = communityService.moderationList(formationId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(posts.size() + " message(s)", posts));
    }

    @DeleteMapping("/api/v1/admin/community/{postId}")
    public ResponseEntity<Void> moderate(@PathVariable UUID postId, Authentication authentication) {
        communityService.moderate(postId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
