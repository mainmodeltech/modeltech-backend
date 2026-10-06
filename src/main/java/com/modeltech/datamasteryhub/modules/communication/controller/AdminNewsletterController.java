package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.communication.dto.response.AdminNewsletterSubscriptionResponse;
import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import com.modeltech.datamasteryhub.modules.communication.service.NewsletterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/newsletter-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin - Newsletter", description = "Abonnés à la newsletter")
@SecurityRequirement(name = "bearerAuth")
public class AdminNewsletterController {

    private final NewsletterService newsletterService;

    @GetMapping
    @Operation(summary = "Lister les abonnés (paginé), filtre facultatif ?status=PENDING|CONFIRMED|UNSUBSCRIBED")
    public ResponseEntity<ApiResponse<List<AdminNewsletterSubscriptionResponse>>> getAll(
            @RequestParam(required = false) NewsletterStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        Page<AdminNewsletterSubscriptionResponse> page = newsletterService.findAllForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " abonné(s)", page));
    }
}
