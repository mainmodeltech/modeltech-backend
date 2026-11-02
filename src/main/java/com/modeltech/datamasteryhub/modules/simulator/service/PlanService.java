package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.AdminPlanView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PlanUpdateRequest;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PlanView;
import com.modeltech.datamasteryhub.modules.simulator.dto.OrderPayloads.PlansView;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimPlan;
import com.modeltech.datamasteryhub.modules.simulator.payment.PaymentGateway;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** Forfaits en vente : lecture publique (la page affiche les prix réellement facturés) et modification par un gestionnaire, tracée. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final SimPlanRepository plans;
    private final PaymentGateway gateway;
    private final AuditService audit;

    public PlansView publicPlans() {
        List<PlanView> items = plans.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(p -> new PlanView(p.getCode(), p.getName(), p.getPriceXof(), p.getDurationDays(), p.getExamAttempts())).toList();
        return new PlansView(gateway.configured(), items);
    }

    public List<AdminPlanView> adminPlans() {
        return plans.findAllByOrderByDisplayOrderAsc().stream().map(PlanService::view).toList();
    }

    @Transactional
    public AdminPlanView update(PlanCode code, PlanUpdateRequest req, String actor) {
        SimPlan p = plans.findById(code).orElseThrow(() -> new ResourceNotFoundException("Forfait", "code", code));
        if (!req.active() && plans.findAllByActiveTrueOrderByDisplayOrderAsc().stream().allMatch(a -> a.getCode() == code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Au moins un forfait doit rester en vente.");
        }
        String before = p.getName() + " " + p.getPriceXof() + " XOF, " + p.getDurationDays() + " j, " + p.getExamAttempts() + " examen(s), " + (p.isActive() ? "en vente" : "retiré");
        p.setName(req.name().trim());
        p.setPriceXof(req.priceXof());
        p.setDurationDays(req.durationDays());
        p.setExamAttempts(req.examAttempts());
        p.setActive(req.active());
        p.setUpdatedAt(LocalDateTime.now());
        p.setUpdatedBy(actor);
        plans.save(p);
        audit.record(AuditAction.SIM_PLAN_UPDATED, "SimPlan", code.name(),
                "Forfait " + code + " modifié : avant « " + before + " », après « " + p.getName() + " " + p.getPriceXof() + " XOF, " + p.getDurationDays()
                        + " j, " + p.getExamAttempts() + " examen(s), " + (p.isActive() ? "en vente" : "retiré") + " » (les commandes en cours gardent leur prix)");
        return view(p);
    }

    private static AdminPlanView view(SimPlan p) {
        return new AdminPlanView(p.getCode(), p.getName(), p.getPriceXof(), p.getDurationDays(), p.getExamAttempts(), p.isActive(), p.getUpdatedAt(), p.getUpdatedBy());
    }
}
