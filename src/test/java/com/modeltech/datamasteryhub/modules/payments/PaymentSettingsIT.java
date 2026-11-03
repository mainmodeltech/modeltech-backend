package com.modeltech.datamasteryhub.modules.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Réglages de paiement : réservés à la Direction, motif obligatoire, audités, et ils changent aussitôt ce que voit le public. */
class PaymentSettingsIT extends AbstractIntegrationTest {

    private static final String SETTINGS = "/api/v1/admin/payment-settings";
    private static final String PLANS = "/api/v1/simulator/plans";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private GatewayHealth health;
    @Autowired private AuditRepository audit;
    @MockBean private PaymentGateway gateway;

    private static RequestPostProcessor direction() {
        return TestAuth.as("dir@test.local", "SUPER_ADMIN");
    }

    @BeforeEach
    void gateway() {
        health.reset();
        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
        when(gateway.notificationUrl()).thenReturn("https://api.test/api/v1/payments/paydunya/ipn");
    }

    private ResultActions change(boolean online, String modes, String reason) throws Exception {
        return mockMvc.perform(put(SETTINGS).with(direction()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"onlineEnabled\":" + online + ",\"modes\":" + modes + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}"));
    }

    private JsonNode plans() throws Exception {
        return json(mockMvc.perform(get(PLANS)).andExpect(status().isOk())).at("/data");
    }

    @Test
    void onlyTheDirectionCanReadOrChangeThePaymentSettings() throws Exception {
        for (String profile : new String[]{"FINANCE", "ADMIN", "PEDAGOGY", "ADMISSIONS", "AUDITOR"}) {
            mockMvc.perform(get(SETTINGS).with(TestAuth.as("x@test.local", profile))).andExpect(status().isForbidden());
            mockMvc.perform(put(SETTINGS).with(TestAuth.as("x@test.local", profile)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"onlineEnabled\":false,\"modes\":{},\"reason\":\"test\"}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(SETTINGS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SETTINGS).with(direction())).andExpect(status().isOk());
    }

    @Test
    void theDefaultsKeepTodaysBehaviour_andShowTheStateOfTheGatewayWithoutAnySecret() throws Exception {
        JsonNode s = json(mockMvc.perform(get(SETTINGS).with(direction())).andExpect(status().isOk())).at("/data");
        assertThat(s.at("/onlineEnabled").asBoolean()).isTrue();
        assertThat(s.at("/services")).hasSize(3);
        assertThat(s.at("/services/0/service").asText()).isEqualTo("SIMULATOR");
        assertThat(s.at("/services/0/mode").asText()).isEqualTo("ONLINE_ONLY");        // le simulateur est déjà payé en ligne
        assertThat(s.at("/services/1/mode").asText()).isEqualTo("MANUAL_ONLY");        // inscriptions : manuel, comme aujourd'hui
        assertThat(s.at("/services/2/mode").asText()).isEqualTo("MANUAL_ONLY");
        assertThat(s.at("/gateway/configured").asBoolean()).isTrue();
        assertThat(s.at("/gateway/mode").asText()).isEqualTo("sandbox");
        assertThat(s.at("/gateway/degraded").asBoolean()).isFalse();
        assertThat(s.at("/fees/mode").asText()).isEqualTo("ABSORB");
        assertThat(s.toString().toLowerCase()).doesNotContain("master").doesNotContain("private").doesNotContain("token");
    }

    @Test
    void aChangeNeedsAReason_isAudited_andChangesWhatVisitorsSeeRightAway() throws Exception {
        change(true, "{\"SIMULATOR\":\"MANUAL_ONLY\"}", null).andExpect(status().isBadRequest());
        change(true, "{\"SIMULATOR\":\"MANUAL_ONLY\"}", "  ").andExpect(status().isBadRequest());

        change(true, "{\"SIMULATOR\":\"MANUAL_ONLY\"}", "PayDunya en maintenance cette semaine").andExpect(status().isOk());
        JsonNode manual = plans();
        assertThat(manual.at("/online").asBoolean()).isFalse();
        assertThat(manual.at("/manual").asBoolean()).isTrue();
        assertThat(manual.at("/fallback").asBoolean()).isFalse();           // choix voulu, pas une panne

        change(true, "{\"SIMULATOR\":\"ONLINE_AND_MANUAL\"}", "Les deux moyens").andExpect(status().isOk());
        JsonNode both = plans();
        assertThat(both.at("/online").asBoolean()).isTrue();
        assertThat(both.at("/manual").asBoolean()).isTrue();

        // coupure d'urgence : l'interrupteur général passe devant les réglages par service
        change(false, "{\"SIMULATOR\":\"ONLINE_ONLY\"}", "Incident chez le prestataire").andExpect(status().isOk());
        JsonNode cut = plans();
        assertThat(cut.at("/online").asBoolean()).isFalse();
        assertThat(cut.at("/manual").asBoolean()).isTrue();
        assertThat(cut.at("/fallback").asBoolean()).isTrue();
        assertThat(cut.at("/fallbackReason").asText()).contains("désactivé");

        assertThat(audit.findAll()).filteredOn(a -> a.getAction().equals("PAYMENT_SETTINGS_CHANGED")).hasSize(3)
                .anySatisfy(a -> assertThat(a.getSummary()).contains("Incident chez le prestataire").contains("dir@test.local"));
        // rien changé : pas de ligne d'audit en plus
        change(false, "{\"SIMULATOR\":\"ONLINE_ONLY\"}", "Rien de nouveau").andExpect(status().isOk());
        assertThat(audit.findAll()).filteredOn(a -> a.getAction().equals("PAYMENT_SETTINGS_CHANGED")).hasSize(3);
    }

    @Test
    void onlinePaymentCannotBeSwitchedOnWhileTheGatewayIsNotConfigured() throws Exception {
        when(gateway.configured()).thenReturn(false);
        change(true, "{\"REGISTRATION\":\"ONLINE_AND_MANUAL\"}", "Essai").andExpect(status().isConflict());
        change(true, "{\"REGISTRATION\":\"MANUAL_ONLY\"}", "Rester en manuel").andExpect(status().isOk());      // revenir au manuel reste possible
    }

    @Test
    void afterThreeGatewayFailuresInARow_manualPaymentTakesOverByItself_untilTheDirectionResetsIt() throws Exception {
        doThrow(new GatewayException("injoignable")).when(gateway).createCheckout(any());
        String order = "{\"plan\":\"PRO\",\"firstName\":\"Awa\",\"email\":\"%s@test.local\",\"acceptTerms\":true}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON).content(order.formatted("awa" + i))).andExpect(status().isBadGateway());
        }
        JsonNode down = plans();
        assertThat(down.at("/online").asBoolean()).isFalse();
        assertThat(down.at("/fallback").asBoolean()).isTrue();
        assertThat(down.at("/fallbackReason").asText()).contains("problème");
        assertThat(json(mockMvc.perform(get(SETTINGS).with(direction()))).at("/data/gateway/degraded").asBoolean()).isTrue();
        // le visiteur suivant n'attend plus la passerelle : sa commande est directement manuelle
        assertThat(json(mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON).content(order.formatted("moussa")))
                .andExpect(status().isCreated())).at("/data/channel").asText()).isEqualTo("MANUAL");

        mockMvc.perform(post(SETTINGS + "/gateway/reset").with(TestAuth.as("fin@test.local", "FINANCE"))).andExpect(status().isForbidden());
        mockMvc.perform(post(SETTINGS + "/gateway/reset").with(direction())).andExpect(status().isOk());
        assertThat(plans().at("/online").asBoolean()).isTrue();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
