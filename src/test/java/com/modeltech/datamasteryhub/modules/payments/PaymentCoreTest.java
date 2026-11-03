package com.modeltech.datamasteryhub.modules.payments;

import com.modeltech.datamasteryhub.modules.payments.entity.PaymentServiceSetting;
import com.modeltech.datamasteryhub.modules.payments.entity.PaymentSetting;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.repository.PaymentServiceSettingRepository;
import com.modeltech.datamasteryhub.modules.payments.repository.PaymentSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Frais, disjoncteur de la passerelle et règle « quels moyens de paiement sont proposés » (sans Spring). */
class PaymentCoreTest {

    // ── Frais ────────────────────────────────────────────────────────

    @Test
    void absorbedFees_leaveThePriceUntouched() {
        PaymentFees fees = new PaymentFees("ABSORB", new BigDecimal("3.5"), 100);
        assertThat(fees.onlineTotal(45000)).isEqualTo(45000);
        assertThat(fees.onlineFee(45000)).isZero();
    }

    @Test
    void passedOnFees_chargeTheAmountThatLeavesExactlyThePriceAfterFees() {
        PaymentFees fees = new PaymentFees("PASS_ON", new BigDecimal("3.5"), 0);
        long total = fees.onlineTotal(45000);
        assertThat(total).isEqualTo(46633);                        // 45 000 / 0,965 arrondi à l'unité supérieure
        assertThat(Math.round(total * 0.965)).isGreaterThanOrEqualTo(45000);    // après 3,5 % de frais, il reste au moins le prix
        assertThat(fees.onlineFee(45000)).isEqualTo(1633);
        // part fixe en plus : (prix + fixe) / (1 - taux)
        assertThat(new PaymentFees("PASS_ON", new BigDecimal("2"), 200).onlineTotal(10000)).isEqualTo(10409);
        // taux et part fixe nuls : rien à répercuter
        assertThat(new PaymentFees("PASS_ON", BigDecimal.ZERO, 0).onlineTotal(25000)).isEqualTo(25000);
    }

    @Test
    void aNonsensicalFeeSettingStopsTheStartup() {
        assertThatThrownBy(() -> new PaymentFees("GRATUIT", BigDecimal.ONE, 0)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ABSORB");
        assertThatThrownBy(() -> new PaymentFees("PASS_ON", new BigDecimal("25"), 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentFees("PASS_ON", new BigDecimal("-1"), 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentFees("PASS_ON", BigDecimal.ONE, 999999)).isInstanceOf(IllegalStateException.class);
        assertThat(new PaymentFees(" pass_on ", BigDecimal.ONE, 0).mode()).isEqualTo(PaymentFees.Mode.PASS_ON);
    }

    // ── Disjoncteur ──────────────────────────────────────────────────

    @Test
    void theBreakerOpensAfterThreeFailuresInARow_andAnySuccessClosesIt() {
        GatewayHealth health = new GatewayHealth();
        health.recordFailure();
        health.recordFailure();
        assertThat(health.isDegraded()).isFalse();
        health.recordSuccess();                      // un succès remet le compteur à zéro
        health.recordFailure();
        health.recordFailure();
        assertThat(health.isDegraded()).isFalse();
        health.recordFailure();
        assertThat(health.isDegraded()).isTrue();
        health.reset();
        assertThat(health.isDegraded()).isFalse();
    }

    // ── Politique de choix du moyen de paiement ──────────────────────

    private PaymentSettingRepository settings;
    private PaymentServiceSettingRepository serviceSettings;
    private PaymentGateway gateway;
    private GatewayHealth health;
    private PaymentPolicy policy;

    @BeforeEach
    void policy() {
        settings = mock(PaymentSettingRepository.class);
        serviceSettings = mock(PaymentServiceSettingRepository.class);
        gateway = mock(PaymentGateway.class);
        health = new GatewayHealth();
        policy = new PaymentPolicy(settings, serviceSettings, gateway, health);
        enabled(true);
        configured(true);
    }

    private void enabled(boolean on) {
        PaymentSetting s = new PaymentSetting();
        s.setOnlineEnabled(on);
        when(settings.findById(PaymentSetting.SINGLETON)).thenReturn(Optional.of(s));
    }

    private void configured(boolean yes) {
        when(gateway.configured()).thenReturn(yes);
    }

    private void mode(PayableService service, PaymentMode mode) {
        PaymentServiceSetting row = new PaymentServiceSetting();
        row.setService(service);
        row.setMode(mode);
        when(serviceSettings.findById(service)).thenReturn(Optional.of(row));
    }

    @Test
    void manualOnly_neverOffersTheGateway() {
        mode(PayableService.REGISTRATION, PaymentMode.MANUAL_ONLY);
        assertThat(policy.optionsFor(PayableService.REGISTRATION, false)).isEqualTo(new PaymentOptions(false, true, false, null));
    }

    @Test
    void anUnsetService_defaultsToManualOnly() {
        when(serviceSettings.findById(PayableService.COACHING)).thenReturn(Optional.empty());
        assertThat(policy.optionsFor(PayableService.COACHING, false).online()).isFalse();
        assertThat(policy.optionsFor(PayableService.COACHING, false).manual()).isTrue();
    }

    @Test
    void onlineAndManual_offersBoth() {
        mode(PayableService.SIMULATOR, PaymentMode.ONLINE_AND_MANUAL);
        PaymentOptions o = policy.optionsFor(PayableService.SIMULATOR, false);
        assertThat(o.online()).isTrue();
        assertThat(o.manual()).isTrue();
        assertThat(o.fallback()).isFalse();
    }

    @Test
    void onlineOnly_offersOnlyOnline_untilSomethingGoesWrong_thenManualTakesOver() {
        mode(PayableService.SIMULATOR, PaymentMode.ONLINE_ONLY);
        assertThat(policy.optionsFor(PayableService.SIMULATOR, false)).isEqualTo(new PaymentOptions(true, false, false, null));

        enabled(false);                               // interrupteur général coupé
        PaymentOptions off = policy.optionsFor(PayableService.SIMULATOR, false);
        assertThat(off.online()).isFalse();
        assertThat(off.manual()).isTrue();
        assertThat(off.fallback()).isTrue();
        assertThat(off.reason()).contains("désactivé").contains("Wave");

        enabled(true);
        configured(false);                            // clés absentes
        assertThat(policy.optionsFor(PayableService.SIMULATOR, false).reason()).contains("pas encore disponible");

        configured(true);
        health.recordFailure();
        health.recordFailure();
        health.recordFailure();                       // passerelle en panne
        PaymentOptions down = policy.optionsFor(PayableService.SIMULATOR, false);
        assertThat(down.online()).isFalse();
        assertThat(down.manual()).isTrue();
        assertThat(down.reason()).contains("problème");
    }

    @Test
    void aCompanyPayer_isAlwaysManual_withoutAnyFallbackMessage() {
        mode(PayableService.REGISTRATION, PaymentMode.ONLINE_ONLY);
        PaymentOptions o = policy.optionsFor(PayableService.REGISTRATION, true);
        assertThat(o.online()).isFalse();
        assertThat(o.manual()).isTrue();
        assertThat(o.fallback()).isFalse();           // une entreprise paie sur facture : ce n'est pas une panne
        assertThat(o.reason()).isNull();
    }

    @Test
    void thereIsAlwaysAtLeastOneWayToPay() {
        for (PaymentMode m : PaymentMode.values()) {
            for (boolean on : new boolean[]{true, false}) {
                for (boolean keys : new boolean[]{true, false}) {
                    for (boolean company : new boolean[]{true, false}) {
                        mode(PayableService.SIMULATOR, m);
                        enabled(on);
                        configured(keys);
                        PaymentOptions o = policy.optionsFor(PayableService.SIMULATOR, company);
                        assertThat(o.online() || o.manual()).as("%s on=%s keys=%s company=%s", m, on, keys, company).isTrue();
                    }
                }
            }
        }
    }
}
