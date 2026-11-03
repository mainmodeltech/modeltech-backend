package com.modeltech.datamasteryhub.modules.payments;

import com.modeltech.datamasteryhub.modules.audit.service.AuditAction;
import com.modeltech.datamasteryhub.modules.audit.service.AuditService;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.FeesInfo;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.GatewayInfo;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.PaymentSettingsUpdate;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.PaymentSettingsView;
import com.modeltech.datamasteryhub.modules.payments.dto.PaymentSettingsPayloads.ServiceSettingView;
import com.modeltech.datamasteryhub.modules.payments.entity.PaymentServiceSetting;
import com.modeltech.datamasteryhub.modules.payments.entity.PaymentSetting;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.repository.PaymentServiceSettingRepository;
import com.modeltech.datamasteryhub.modules.payments.repository.PaymentSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lecture et modification des réglages de paiement. Réservé à la Direction (droit {@code PAYMENT_SETTINGS_MANAGE}) ; chaque changement est tracé avec son motif. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentSettingsService {

    private final PaymentSettingRepository settings;
    private final PaymentServiceSettingRepository serviceSettings;
    private final PaymentGateway gateway;
    private final GatewayHealth health;
    private final PaymentFees fees;
    private final AuditService audit;

    public PaymentSettingsView current() {
        PaymentSetting s = settings.findById(PaymentSetting.SINGLETON).orElseGet(PaymentSetting::new);
        List<ServiceSettingView> services = new ArrayList<>();
        for (PayableService svc : PayableService.values()) {
            PaymentServiceSetting row = serviceSettings.findById(svc).orElse(null);
            PaymentMode mode = row == null ? PaymentMode.MANUAL_ONLY : row.getMode();
            services.add(new ServiceSettingView(svc, svc.label(), mode, mode.label(), row == null ? null : row.getUpdatedAt(), row == null ? null : row.getUpdatedBy()));
        }
        return new PaymentSettingsView(s.isOnlineEnabled(), s.getUpdatedAt(), s.getUpdatedBy(), services,
                new GatewayInfo(gateway.provider(), gateway.configured(), gateway.mode(), health.isDegraded(), gateway.notificationUrl()),
                new FeesInfo(fees.mode().name(), fees.percent(), fees.fixedXof()));
    }

    @Transactional
    public PaymentSettingsView update(PaymentSettingsUpdate req, String actor) {
        Map<PayableService, PaymentMode> wanted = req.modes() == null ? Map.of() : req.modes();
        boolean needsOnline = req.onlineEnabled() && wanted.values().stream().anyMatch(PaymentMode::wantsOnline);
        if (needsOnline && !gateway.configured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La passerelle de paiement n'est pas configurée sur ce serveur (clés absentes) : le paiement en ligne ne peut pas être activé.");
        }
        PaymentSetting s = settings.findById(PaymentSetting.SINGLETON).orElseGet(PaymentSetting::new);
        List<String> changes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        if (s.isOnlineEnabled() != req.onlineEnabled()) {
            changes.add("interrupteur général " + (s.isOnlineEnabled() ? "oui" : "non") + " → " + (req.onlineEnabled() ? "oui" : "non"));
        }
        s.setOnlineEnabled(req.onlineEnabled());
        s.setUpdatedAt(now);
        s.setUpdatedBy(actor);
        settings.save(s);

        for (Map.Entry<PayableService, PaymentMode> e : wanted.entrySet()) {
            PaymentServiceSetting row = serviceSettings.findById(e.getKey()).orElseGet(() -> {
                PaymentServiceSetting n = new PaymentServiceSetting();
                n.setService(e.getKey());
                return n;
            });
            if (row.getMode() != e.getValue()) {
                changes.add(e.getKey().label() + " : " + row.getMode().label() + " → " + e.getValue().label());
                row.setMode(e.getValue());
                row.setUpdatedAt(now);
                row.setUpdatedBy(actor);
                serviceSettings.save(row);
            }
        }
        if (changes.isEmpty()) return current();
        audit.record(AuditAction.PAYMENT_SETTINGS_CHANGED, "PaymentSetting", PaymentSetting.SINGLETON,
                "Réglages de paiement modifiés par " + actor + " (" + String.join(" ; ", changes) + ") — motif : " + req.reason().trim());
        return current();
    }

    /** Referme le disjoncteur à la main, quand on sait que la passerelle est rétablie. */
    @Transactional
    public PaymentSettingsView resetGateway(String actor) {
        health.reset();
        audit.record(AuditAction.PAYMENT_SETTINGS_CHANGED, "PaymentSetting", PaymentSetting.SINGLETON, "Alerte de la passerelle de paiement remise à zéro par " + actor);
        return current();
    }
}
