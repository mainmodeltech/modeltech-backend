package com.modeltech.datamasteryhub.modules.cms;

import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.cms.service.SiteContentDefaults;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contenus par défaut du site : amorçage sans écrasement, liste et restauration depuis l'administration. */
class SiteContentDefaultsIT extends AbstractIntegrationTest {

    private static final String PUBLIC = "/api/v1/site-settings";
    private static final String ADMIN = "/api/v1/admin/site-settings";

    @Autowired
    private SiteContentDefaults defaults;

    @Test
    void shippedDefaults_coverEveryPage_andFitTheSizeLimit() {
        var all = defaults.all();
        assertThat(all.keySet()).contains("site.company", "site.nav", "page.home", "page.contact", "page.formations",
                "page.orientation", "page.masterclass");
        all.values().forEach(value ->
                assertThat(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(20 * 1024));
    }

    @Test
    void seedMissing_createsAbsentKeys_andNeverOverwritesAnExistingValue() throws Exception {
        putSetting("page.contact", "{\"value\":{\"custom\":true}}").andExpect(status().isOk());

        int created = defaults.seedMissing();

        assertThat(created).isEqualTo(defaults.all().size() - 1);
        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['page.contact'].custom").value(true))
                .andExpect(jsonPath("$.data['site.company'].name").exists());
        assertThat(defaults.seedMissing()).isZero();
    }

    @Test
    void defaultsEndpoint_listsKeys_andResetRestoresTheShippedValue() throws Exception {
        mockMvc.perform(get(ADMIN + "/defaults").with(admin("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@ == 'page.home')]").exists());

        putSetting("page.home", "{\"value\":{\"custom\":true}}").andExpect(status().isOk());
        mockMvc.perform(post(ADMIN + "/page.home/reset").with(admin("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value.custom").doesNotExist());

        mockMvc.perform(get(PUBLIC)).andExpect(jsonPath("$.data['page.home'].custom").doesNotExist());
    }

    @Test
    void reset_unknownKey_isNotFound_andRequiresABackOfficeRole() throws Exception {
        mockMvc.perform(post(ADMIN + "/inconnue/reset").with(admin("EDITOR"))).andExpect(status().isNotFound());
        mockMvc.perform(post(ADMIN + "/page.home/reset")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN + "/page.home/reset").with(admin("LEARNER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN + "/defaults").with(admin("LEARNER"))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions putSetting(String key, String body) throws Exception {
        return mockMvc.perform(put(ADMIN + "/" + key).with(admin("EDITOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor admin(String role) {
        return TestAuth.as("staff@test.local", role);
    }
}
