package com.modeltech.datamasteryhub.modules.notification.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.notification.dto.EmailLogResponse;
import com.modeltech.datamasteryhub.modules.notification.dto.TestEmailRequest;
import com.modeltech.datamasteryhub.modules.notification.dto.TestEmailResponse;
import com.modeltech.datamasteryhub.modules.notification.service.EmailLogService;
import com.modeltech.datamasteryhub.modules.notification.service.ResilientMailSender;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Journal des e-mails sortants et diagnostic de la messagerie (ADMIN ; le test d'envoi est réservé aux SUPER_ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/email-logs")
@RequiredArgsConstructor
@Tag(name = "Admin - E-mails")
public class AdminEmailLogController {

    private final EmailLogService emailLogService;
    private final IpRateLimiter rateLimiter;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmailLogResponse>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmailLogResponse> page = emailLogService.findAll(status, type, pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " e-mail(s)", page));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ResilientMailSender.MailStatus>> status() {
        return ResponseEntity.ok(ApiResponse.ok("État de la messagerie", emailLogService.status()));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<TestEmailResponse>> sendTest(
            @Valid @RequestBody TestEmailRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest, "mail-test");
        TestEmailResponse result = emailLogService.sendTest(request.getTo());
        return ResponseEntity.ok(ApiResponse.ok(result.getMessage(), result));
    }
}
