package com.modeltech.datamasteryhub.modules.legal.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.LegalContext;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.DocumentRef;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.PublicDocument;
import com.modeltech.datamasteryhub.modules.legal.dto.LegalPayloads.Requirements;
import com.modeltech.datamasteryhub.modules.legal.service.LegalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Textes légaux en vigueur, lisibles par tous, et textes à accepter avant un acte. */
@RestController
@RequestMapping("/api/v1/legal")
@RequiredArgsConstructor
@Tag(name = "Public - Textes légaux")
public class PublicLegalController {

    private final LegalService legal;

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DocumentRef>>> published() {
        return ResponseEntity.ok(ApiResponse.ok("Textes légaux en vigueur", legal.published()));
    }

    @GetMapping("/documents/{code}")
    public ResponseEntity<ApiResponse<PublicDocument>> document(@PathVariable LegalCode code) {
        return ResponseEntity.ok(ApiResponse.ok("Texte légal", legal.publishedBody(code)));
    }

    @GetMapping("/requirements")
    public ResponseEntity<ApiResponse<Requirements>> requirements(@RequestParam LegalContext context) {
        return ResponseEntity.ok(ApiResponse.ok("Textes à accepter", legal.requirements(context)));
    }
}
