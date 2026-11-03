package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.common.ratelimit.IpRateLimiter;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.DeclareRequest;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.OrderStatusView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PlansView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PurchaseRequest;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PurchaseResponse;
import com.modeltech.datamasteryhub.modules.simulator.service.OrderService;
import com.modeltech.datamasteryhub.modules.simulator.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
@Tag(name = "Simulateur (Public)", description = "Forfaits et achat en ligne")
public class PublicSimulatorOrderController {

    private final PlanService plans;
    private final OrderService orders;
    private final IpRateLimiter rateLimiter;

    @GetMapping("/plans")
    @Operation(summary = "Forfaits en vente (prix réellement facturés) et disponibilité du paiement en ligne")
    public ResponseEntity<ApiResponse<PlansView>> plans() {
        return ResponseEntity.ok(ApiResponse.ok("Forfaits", plans.publicPlans()));
    }

    @PostMapping("/orders")
    @Operation(summary = "Commander un forfait",
            description = "Crée la commande et le paiement chez le prestataire ; renvoie l'adresse de paiement vers laquelle rediriger. "
                    + "Le prix vient du serveur. L'accès ne s'ouvre qu'après confirmation du paiement par le prestataire. Limité par IP.")
    public ResponseEntity<ApiResponse<PurchaseResponse>> order(@Valid @RequestBody PurchaseRequest request, HttpServletRequest http) {
        rateLimiter.check(http, "simulator-order");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Commande créée", orders.create(request)));
    }

    @PostMapping("/orders/{ref}/declare")
    @Operation(summary = "Déclarer un paiement manuel (Wave, Orange Money, virement)",
            description = "Réservé aux commandes payées manuellement : moyen + référence de l'opération. L'équipe vérifie puis confirme ; l'accès ne s'ouvre qu'à la confirmation.")
    public ResponseEntity<ApiResponse<OrderStatusView>> declare(@PathVariable UUID ref, @Valid @RequestBody DeclareRequest request, HttpServletRequest http) {
        rateLimiter.check(http, "simulator-order-declare");
        return ResponseEntity.ok(ApiResponse.ok("Paiement déclaré : nous le vérifions", orders.declare(ref, request)));
    }

    @PostMapping(value = "/orders/{ref}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Joindre le justificatif d'un paiement manuel (image)")
    public ResponseEntity<ApiResponse<OrderStatusView>> proof(@PathVariable UUID ref, @RequestPart("file") MultipartFile file, HttpServletRequest http) {
        rateLimiter.check(http, "simulator-order-proof");
        return ResponseEntity.ok(ApiResponse.ok("Justificatif enregistré", orders.uploadProof(ref, file)));
    }

    @GetMapping("/orders/{ref}")
    @Operation(summary = "Suivre une commande par sa référence opaque",
            description = "Relit l'état du paiement auprès du prestataire si besoin. Ne révèle qu'une adresse e-mail masquée.")
    public ResponseEntity<ApiResponse<OrderStatusView>> status(@PathVariable UUID ref, HttpServletRequest http) {
        rateLimiter.check(http, "simulator-order-status");
        return ResponseEntity.ok(ApiResponse.ok("Commande", orders.status(ref)));
    }
}
