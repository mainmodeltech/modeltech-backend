package com.modeltech.datamasteryhub.modules.payments;

import com.modeltech.datamasteryhub.modules.payments.entity.PaymentServiceSetting;
import com.modeltech.datamasteryhub.modules.payments.entity.PaymentSetting;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.repository.PaymentServiceSettingRepository;
import com.modeltech.datamasteryhub.modules.payments.repository.PaymentSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Règle unique, partagée par tous les services, qui décide quels moyens de paiement sont proposés.
 * <ol>
 *   <li>Le paiement en ligne n'est proposé que s'il est voulu pour le service, que l'interrupteur général est sur « oui », que les clés de la
 *       passerelle sont configurées, que la passerelle n'est pas en panne (disjoncteur) et que le payeur n'est pas une entreprise
 *       (facture / bon de commande : toujours manuel).</li>
 *   <li>Le manuel est proposé s'il est voulu pour le service <b>ou</b> si l'en ligne est indisponible : un client n'est jamais bloqué sans moyen de payer.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentPolicy {

    private final PaymentSettingRepository settings;
    private final PaymentServiceSettingRepository serviceSettings;
    private final PaymentGateway gateway;
    private final GatewayHealth health;

    public PaymentMode modeOf(PayableService service) {
        return serviceSettings.findById(service).map(PaymentServiceSetting::getMode).orElse(PaymentMode.MANUAL_ONLY);
    }

    public boolean globalOnlineEnabled() {
        return settings.findById(PaymentSetting.SINGLETON).map(PaymentSetting::isOnlineEnabled).orElse(false);
    }

    public PaymentOptions optionsFor(PayableService service, boolean companyPayer) {
        PaymentMode mode = modeOf(service);
        boolean wantsOnline = mode.wantsOnline() && !companyPayer;
        String blocker = null;
        if (wantsOnline) {
            if (!globalOnlineEnabled()) blocker = "Le paiement en ligne est momentanément désactivé.";
            else if (!gateway.configured()) blocker = "Le paiement en ligne n'est pas encore disponible.";
            else if (health.isDegraded()) blocker = "Le paiement en ligne rencontre un problème momentané.";
        }
        boolean online = wantsOnline && blocker == null;
        boolean manual = mode.manualAlways() || !online;
        boolean fallback = wantsOnline && !online;
        return new PaymentOptions(online, manual, fallback, fallback ? blocker + " Vous pouvez payer par Wave, Orange Money ou virement." : null);
    }
}
