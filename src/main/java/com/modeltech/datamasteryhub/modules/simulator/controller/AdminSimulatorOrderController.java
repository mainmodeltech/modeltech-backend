package com.modeltech.datamasteryhub.modules.simulator.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.security.Actor;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.AdminPlanView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.GatewayStatus;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.OrderView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PlanUpdateRequest;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.ProofLink;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.RejectOrderRequest;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.simulator.service.OrderService;
import com.modeltech.datamasteryhub.modules.simulator.service.PlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Forfaits, commandes en ligne et état de la passerelle de paiement. Les droits sont vérifiés par {@code AdminAccessRules}. */
@RestController
@RequestMapping("/api/v1/admin/simulator")
@RequiredArgsConstructor
@Tag(name = "Simulateur (Admin)")
public class AdminSimulatorOrderController {

    private final PlanService plans;
    private final OrderService orders;
    private final PaymentGateway gateway;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<AdminPlanView>>> plans() {
        return ResponseEntity.ok(ApiResponse.ok("Forfaits", plans.adminPlans()));
    }

    @PutMapping("/plans/{code}")
    public ResponseEntity<ApiResponse<AdminPlanView>> updatePlan(@PathVariable PlanCode code, @Valid @RequestBody PlanUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Forfait mis à jour", plans.update(code, request, Actor.email())));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderView>>> orders(@RequestParam(required = false) OrderStatus status,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        Page<OrderView> result = orders.list(status, page, size);
        return ResponseEntity.ok(ApiResponse.page(result.getTotalElements() + " commande(s)", result));
    }

    @PostMapping("/orders/{id}/verify")
    public ResponseEntity<ApiResponse<OrderView>> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Commande revérifiée", orders.verify(id, Actor.email())));
    }

    /** Confirme un paiement manuel après avoir vu l'argent : ouvre l'accès. */
    @PostMapping("/orders/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderView>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Paiement confirmé : accès ouvert", orders.confirm(id, Actor.email())));
    }

    /** Refuse un paiement déclaré (montant ou justificatif non conforme) : l'acheteur peut déclarer à nouveau. */
    @PostMapping("/orders/{id}/reject")
    public ResponseEntity<ApiResponse<OrderView>> reject(@PathVariable UUID id, @Valid @RequestBody RejectOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Paiement refusé", orders.reject(id, request.reason(), Actor.email())));
    }

    @GetMapping("/orders/{id}/proof")
    public ResponseEntity<ApiResponse<ProofLink>> proof(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Justificatif", new ProofLink(orders.proofLink(id))));
    }

    @GetMapping("/payments/status")
    public ResponseEntity<ApiResponse<GatewayStatus>> gateway() {
        return ResponseEntity.ok(ApiResponse.ok("Passerelle de paiement",
                new GatewayStatus(gateway.provider(), gateway.configured(), gateway.mode(), gateway.notificationUrl())));
    }
}
