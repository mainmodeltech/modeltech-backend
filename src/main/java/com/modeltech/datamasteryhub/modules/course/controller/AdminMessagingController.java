package com.modeltech.datamasteryhub.modules.course.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads;
import com.modeltech.datamasteryhub.modules.course.service.MessagingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Messages aux apprenants d'une session et questions des apprenants (administration, formateur de la session). */
@RestController
@RequestMapping("/api/v1/admin/sessions/{sessionId}")
@RequiredArgsConstructor
@Tag(name = "Admin - Messagerie des sessions")
public class AdminMessagingController {

    private final MessagingService messagingService;

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessagingPayloads.SessionMessageResponse>> send(
            @PathVariable UUID sessionId, @Valid @RequestBody MessagingPayloads.SessionMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok("Message en cours d'envoi",
                messagingService.sendToSession(sessionId, request, authentication.getName(), roles(authentication))));
    }

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<MessagingPayloads.SessionMessageResponse>>> history(
            @PathVariable UUID sessionId, Authentication authentication) {
        List<MessagingPayloads.SessionMessageResponse> messages = messagingService.history(sessionId, authentication.getName(), roles(authentication));
        return ResponseEntity.ok(ApiResponse.ok(messages.size() + " message(s)", messages));
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<List<MessagingPayloads.SessionQuestion>>> questions(
            @PathVariable UUID sessionId, @RequestParam(defaultValue = "false") boolean open, Authentication authentication) {
        List<MessagingPayloads.SessionQuestion> questions = messagingService.sessionQuestions(sessionId, open, authentication.getName(), roles(authentication));
        return ResponseEntity.ok(ApiResponse.ok(questions.size() + " question(s)", questions));
    }

    @PostMapping("/questions/{questionId}/answer")
    public ResponseEntity<ApiResponse<MessagingPayloads.SessionQuestion>> answer(
            @PathVariable UUID sessionId, @PathVariable UUID questionId,
            @Valid @RequestBody MessagingPayloads.AnswerRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Réponse envoyée",
                messagingService.answer(sessionId, questionId, request.getAnswer(), authentication.getName(), roles(authentication))));
    }

    private List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
