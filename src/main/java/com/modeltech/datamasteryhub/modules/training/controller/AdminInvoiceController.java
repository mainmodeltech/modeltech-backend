package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.training.dto.InvoicePayloads;
import com.modeltech.datamasteryhub.modules.training.service.InvoiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Factures PDF des entreprises (SUPER_ADMIN et ADMIN). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Factures")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/registrations/{registrationId}/invoice")
    public ResponseEntity<ApiResponse<InvoicePayloads.InvoiceResponse>> create(
            @PathVariable UUID registrationId,
            @Valid @RequestBody(required = false) InvoicePayloads.CreateRequest request,
            Authentication authentication) {
        InvoicePayloads.CreateRequest body = request != null ? request : new InvoicePayloads.CreateRequest();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Facture émise",
                invoiceService.create(registrationId, body, authentication.getName())));
    }

    @GetMapping("/registrations/{registrationId}/invoices")
    public ResponseEntity<ApiResponse<List<InvoicePayloads.InvoiceResponse>>> byRegistration(@PathVariable UUID registrationId) {
        List<InvoicePayloads.InvoiceResponse> invoices = invoiceService.findByRegistration(registrationId);
        return ResponseEntity.ok(ApiResponse.ok(invoices.size() + " facture(s)", invoices));
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<InvoicePayloads.InvoiceResponse>>> getAll(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InvoicePayloads.InvoiceResponse> page = invoiceService.findAll(status, pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " facture(s)", page));
    }

    @GetMapping("/invoices/{number}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String number) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("Facture-" + number + ".pdf").build().toString())
                .body(invoiceService.pdf(number));
    }

    @PostMapping("/invoices/{number}/cancel")
    public ResponseEntity<ApiResponse<InvoicePayloads.InvoiceResponse>> cancel(
            @PathVariable String number, @Valid @RequestBody InvoicePayloads.CancelRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Facture annulée",
                invoiceService.cancel(number, request.getReason(), authentication.getName())));
    }

    @PostMapping("/invoices/{number}/send")
    public ResponseEntity<ApiResponse<Void>> send(
            @PathVariable String number, @Valid @RequestBody(required = false) InvoicePayloads.SendRequest request) {
        invoiceService.send(number, request != null ? request.getTo() : null);
        return ResponseEntity.ok(ApiResponse.ok("Facture envoyée", null));
    }
}
