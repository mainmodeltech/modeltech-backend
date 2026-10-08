package com.modeltech.datamasteryhub.modules.cms;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contenus du site : lecture publique, écriture réservée au back-office. */
class SiteSettingControllerIT extends AbstractIntegrationTest {

    private static final String PUBLIC = "/api/v1/site-settings";
    private static final String ADMIN = "/api/v1/admin/site-settings";
    private static final String PRICES = "{\"value\":{\"deblocage\":\"30 000 FCFA\",\"progression\":null,\"projet\":\"90 000 FCFA\"}}";

    @Test
    void publicRead_isOpen_andEmptyUntilSomethingIsPublished() throws Exception {
        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void upsert_publishesAnyJsonShape_underItsKey() throws Exception {
        putSetting("coaching.prices", PRICES).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("coaching.prices"))
                .andExpect(jsonPath("$.data.value.deblocage").value("30 000 FCFA"));
        putSetting("next-workshop", "{\"value\":{\"title\":\"Atelier Power BI\",\"date\":\"2026-11-14\",\"time\":\"18:00\"}}")
                .andExpect(status().isOk());
        putSetting("home.tagline", "{\"value\":\"Former les talents data\"}").andExpect(status().isOk());
        putSetting("home.partners-order", "{\"value\":[\"a\",\"b\"]}").andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['coaching.prices'].projet").value("90 000 FCFA"))
                .andExpect(jsonPath("$.data['next-workshop'].title").value("Atelier Power BI"))
                .andExpect(jsonPath("$.data['home.tagline']").value("Former les talents data"))
                .andExpect(jsonPath("$.data['home.partners-order'][1]").value("b"));
    }

    @Test
    void upsert_replacesTheValue_ofAnExistingKey() throws Exception {
        putSetting("coach", "{\"value\":{\"name\":\"Awa\"}}").andExpect(status().isOk());
        putSetting("coach", "{\"value\":{\"name\":\"Moussa\"}}").andExpect(status().isOk());

        mockMvc.perform(get(ADMIN).with(admin("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].value.name").value("Moussa"));
    }

    @Test
    void delete_removesTheContentFromThePublicSite_andTheKeyCanBeRecreated() throws Exception {
        putSetting("client-case", "{\"value\":{\"name\":\"ACME\"}}").andExpect(status().isOk());

        mockMvc.perform(delete(ADMIN + "/client-case").with(admin("EDITOR"))).andExpect(status().isNoContent());
        mockMvc.perform(get(PUBLIC)).andExpect(jsonPath("$.data['client-case']").doesNotExist());
        mockMvc.perform(delete(ADMIN + "/client-case").with(admin("EDITOR"))).andExpect(status().isNotFound());

        putSetting("client-case", "{\"value\":{\"name\":\"Nouveau\"}}").andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC)).andExpect(jsonPath("$.data['client-case'].name").value("Nouveau"));
    }

    @Test
    void upsert_validatesTheKeyAndTheValue() throws Exception {
        putSetting("Majuscules", "{\"value\":\"x\"}").andExpect(status().isBadRequest());
        putSetting("avec espace", "{\"value\":\"x\"}").andExpect(status().isBadRequest());
        putSetting("vide", "{}").andExpect(status().isBadRequest());
        putSetting("nul", "{\"value\":null}").andExpect(status().isBadRequest());
        putSetting("enorme", "{\"value\":\"" + "x".repeat(21 * 1024) + "\"}").andExpect(status().isPayloadTooLarge());
    }

    @Test
    void writes_requireABackOfficeRole() throws Exception {
        mockMvc.perform(put(ADMIN + "/coach").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN + "/coach").with(admin("LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN)).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions putSetting(String key, String body) throws Exception {
        return mockMvc.perform(put(ADMIN + "/" + key).with(admin("EDITOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor admin(String role) {
        return SecurityMockMvcRequestPostProcessors.user("staff@test.local").roles(role);
    }
}
