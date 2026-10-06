package com.modeltech.datamasteryhub.modules.training;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD /api/v1/admin/domains. */
class AdminDomainControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/domains";

    @Autowired private DomainRepository domainRepository;
    @Autowired private BootcampRepository bootcampRepository;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_generatesASlugFromTheName_andAppliesDefaults() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intelligence artificielle appliquée\",\"badge\":\"NOUVEAU\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("intelligence-artificielle-appliquee"))
                .andExpect(jsonPath("$.data.comingSoon").value(false))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.displayOrder").value(0));
    }

    @Test
    void create_suffixesTheGeneratedSlugWhenTheNameCollides() throws Exception {
        for (String expected : new String[]{"cyber", "cyber-2"}) {
            mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Cyber\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.slug").value(expected));
        }
    }

    @Test
    void create_rejectsAnExplicitSlugAlreadyUsed_with409() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Doublon\",\"slug\":\"data-bi\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void create_validatesTheBody_with400() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").value("Le nom est obligatoire"));
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"slug\":\"Pas Valide\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_isPaginated_andIncludesTheVisibleFlag() throws Exception {
        mockMvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("data-bi"))
                .andExpect(jsonPath("$.data[0].visible").value(true))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void update_isPartial_andRevalidatesTheSlug() throws Exception {
        Domain other = domainRepository.save(TestData.domain("autre", "Autre", true));

        mockMvc.perform(put(URL + "/" + other.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"badge\":\"BIENTÔT\",\"comingSoon\":true,\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Autre"))
                .andExpect(jsonPath("$.data.badge").value("BIENTÔT"))
                .andExpect(jsonPath("$.data.comingSoon").value(true))
                .andExpect(jsonPath("$.data.visible").value(false));

        mockMvc.perform(put(URL + "/" + other.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"data-bi\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void get_unknownDomain_is404() throws Exception {
        mockMvc.perform(get(URL + "/" + java.util.UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_isRefusedWhileFormationsAreAttached_thenSoftDeletes() throws Exception {
        Domain domain = domainRepository.save(TestData.domain("a-supprimer", "À supprimer", true));
        var bootcamp = bootcampRepository.save(TestData.bootcamp("formation-liee", "Formation liée", domain));

        mockMvc.perform(delete(URL + "/" + domain.getId()).with(admin())).andExpect(status().isConflict());

        bootcamp.setDeleted(true);
        bootcampRepository.save(bootcamp);

        mockMvc.perform(delete(URL + "/" + domain.getId()).with(admin())).andExpect(status().isNoContent());
        assertThat(domainRepository.findById(domain.getId()).orElseThrow().isDeleted()).isTrue();
        mockMvc.perform(get(URL + "/" + domain.getId()).with(admin())).andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.local").roles("ADMIN");
    }
}
