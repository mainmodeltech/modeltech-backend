package com.modeltech.datamasteryhub.modules.training;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD /api/v1/admin/partners (+ logo). */
class AdminPartnerControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/partners";

    @Autowired private PartnerRepository partnerRepository;
    @Autowired private DomainRepository domainRepository;
    @Autowired private BootcampRepository bootcampRepository;

    @MockBean private StorageService storageService;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    void create_exposesInternalFieldsToAdmins_andGeneratesTheSlug() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Agile Sénégal","bio":"Cabinet certifié","website":"https://agile.sn",
                                 "contactName":"Awa","contactEmail":"awa@agile.sn","contactPhone":"+221770000000",
                                 "revenueSharePercent":35.5}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("agile-senegal"))
                .andExpect(jsonPath("$.data.contactEmail").value("awa@agile.sn"))
                .andExpect(jsonPath("$.data.revenueSharePercent").value(35.5))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void create_validatesTheBody() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"revenueSharePercent\":120}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"contactEmail\":\"pas-un-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rejectsADuplicateSlug_with409() throws Exception {
        partnerRepository.save(TestData.partner("deja-pris", "Déjà pris"));

        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Autre\",\"slug\":\"deja-pris\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void list_get_and_update() throws Exception {
        Partner partner = partnerRepository.save(TestData.partner("p1", "Partenaire 1"));

        mockMvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("p1"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        mockMvc.perform(put(URL + "/" + partner.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Nouvelle bio\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Nouvelle bio"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.name").value("Partenaire 1"));

        mockMvc.perform(get(URL + "/" + java.util.UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadLogo_storesTheFileAndDeletesThePreviousOne() throws Exception {
        Partner partner = TestData.partner("avec-logo", "Avec logo");
        partner.setLogoObjectKey("partners/ancien.png");
        partner = partnerRepository.save(partner);
        when(storageService.upload(any(), eq("partners")))
                .thenReturn(new StorageService.UploadResult("partners/nouveau.png", "http://minio/media/partners/nouveau.png"));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart(URL + "/" + partner.getId() + "/logo").file(file).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value("http://minio/media/partners/nouveau.png"));

        verify(storageService).delete("partners/ancien.png");
        assertThat(partnerRepository.findById(partner.getId()).orElseThrow().getLogoObjectKey())
                .isEqualTo("partners/nouveau.png");
    }

    @Test
    void delete_isRefusedWhileFormationsAreDelivered_thenSoftDeletes() throws Exception {
        Partner partner = partnerRepository.save(TestData.partner("a-supprimer", "À supprimer"));
        Domain domain = domainRepository.findAll().get(0);
        var bootcamp = TestData.bootcamp("formation-partenaire", "Formation partenaire", domain);
        bootcamp.setDeliveredBy(DeliveredBy.PARTNER);
        bootcamp.setPartner(partner);
        bootcamp = bootcampRepository.save(bootcamp);

        mockMvc.perform(delete(URL + "/" + partner.getId()).with(admin())).andExpect(status().isConflict());

        bootcamp.setDeleted(true);
        bootcampRepository.save(bootcamp);

        mockMvc.perform(delete(URL + "/" + partner.getId()).with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(get(URL + "/" + partner.getId()).with(admin())).andExpect(status().isNotFound());
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.local").roles("ADMIN");
    }
}
