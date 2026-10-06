package com.modeltech.datamasteryhub.modules.training.controller;

import com.modeltech.datamasteryhub.modules.training.dto.request.CatalogueFilter;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.FormationSessionResponse;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.FormationFormat;
import com.modeltech.datamasteryhub.modules.training.enums.FormationLevel;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalogue public par domaines. Une « formation » est un bootcamp enrichi
 * (domaine, partenaire, niveau, format…) : même identifiant que /bootcamps.
 * Réponses en JSON brut, comme le reste du catalogue public.
 */
@RestController
@RequestMapping("/api/v1/formations")
@RequiredArgsConstructor
@Tag(name = "Formations (Public)", description = "Catalogue par domaines — lecture seule")
public class PublicFormationController {

    private final BootcampService bootcampService;

    @GetMapping
    @Operation(summary = "Catalogue des formations publiées, filtrable",
            description = "Filtres facultatifs : domain (slug), level, format, deliveredBy, targetRole.")
    public ResponseEntity<List<BootcampResponse>> findAll(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) DeliveredBy deliveredBy,
            @RequestParam(required = false) FormationLevel level,
            @RequestParam(required = false) FormationFormat format,
            @RequestParam(required = false) String targetRole) {
        CatalogueFilter filter = new CatalogueFilter(domain, deliveredBy, level, format, targetRole);
        return ResponseEntity.ok(bootcampService.findCatalogue(filter));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Fiche d'une formation par slug")
    public ResponseEntity<BootcampResponse> findBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(bootcampService.findCatalogueBySlug(slug));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Sessions de toutes les formations, à plat, triées par date de début",
            description = "Sessions publiées au statut OPEN ou UPCOMING (vue calendrier).")
    public ResponseEntity<List<FormationSessionResponse>> findAllSessions() {
        return ResponseEntity.ok(bootcampService.findCatalogueSessions());
    }
}
