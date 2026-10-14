package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads;
import com.modeltech.datamasteryhub.modules.course.service.MessagingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Boîte de réception des questions des apprenants, toutes sessions confondues. Pour répondre, utiliser
 * {@code POST /admin/sessions/{sessionId}/questions/{id}/answer} avec la session indiquée sur chaque question.
 */
@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@Tag(name = "Admin - Questions des apprenants")
public class AdminQuestionInboxController {

    private final MessagingService messagingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MessagingPayloads.InboxQuestion>>> inbox(
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        Page<MessagingPayloads.InboxQuestion> result = messagingService.inbox(status, q, sessionId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt")),
                authentication.getName(), authorities);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " question(s)", result));
    }
}
