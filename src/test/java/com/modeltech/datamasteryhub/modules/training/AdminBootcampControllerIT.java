package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/admin/bootcamps — contrôleur modifié par le lot a (champs formation, slug,
 * domaine, partenaire, formations liées) ; vérifie aussi la non-régression de
 * l'ancien formulaire admin (payload sans aucun champ du catalogue).
 */
class AdminBootcampControllerIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/bootcamps";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DomainRepository domainRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private BootcampRepository bootcampRepository;

    private Domain dataBi;
    private Partner partner;

    @BeforeEach
    void setUp() {
        dataBi = domainRepository.findAll().stream().filter(d -> "data-bi".equals(d.getSlug())).findFirst().orElseThrow();
        partner = partnerRepository.save(TestData.partner("agile-senegal", "Agile Sénégal"));
    }

    @Test
    void legacyPayloadWithoutCatalogueFields_stillCreatesABootcamp() throws Exception {
        // Payload de l'ancien formulaire admin : aucun champ domaine/partenaire/niveau
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bootcamp Excel & Finance","description":"d","duration":"5 jours",
                                 "price":"120 000 FCFA","benefits":["a","b"],"category":"excel-finance",
                                 "featured":false,"published":true,"displayOrder":3}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Bootcamp Excel & Finance"))
                .andExpect(jsonPath("$.slug").value("bootcamp-excel-finance"))
                .andExpect(jsonPath("$.deliveredBy").value("INTERNAL"))
                .andExpect(jsonPath("$.partner").doesNotExist())
                .andExpect(jsonPath("$.domain").doesNotExist());
    }

    @Test
    void create_withCatalogueFields() throws Exception {
        UUID relatedId = bootcampRepository.save(TestData.bootcamp("existante", "Existante", dataBi)).getId();

        String body = """
                {"title":"Power BI & Business Intelligence","domainId":"%s","deliveredBy":"INTERNAL",
                 "level":"DEBUTANT","format":"EN_LIGNE","certificationPrep":"PL-300",
                 "targetRoles":["Finance","Audit"],"relatedFormationIds":["%s"]}"""
                .formatted(dataBi.getId(), relatedId);

        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("power-bi-business-intelligence"))
                .andExpect(jsonPath("$.domainId").value(dataBi.getId().toString()))
                .andExpect(jsonPath("$.domain.slug").value("data-bi"))
                .andExpect(jsonPath("$.level").value("DEBUTANT"))
                .andExpect(jsonPath("$.format").value("EN_LIGNE"))
                .andExpect(jsonPath("$.certificationPrep").value("PL-300"))
                .andExpect(jsonPath("$.targetRoles[1]").value("Audit"))
                .andExpect(jsonPath("$.relatedFormationIds[0]").value(relatedId.toString()));
    }

    @Test
    void create_generatesUniqueSlugsFromIdenticalTitles() throws Exception {
        for (String expected : new String[]{"power-bi", "power-bi-2", "power-bi-3"}) {
            mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Power BI\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.slug").value(expected));
        }
    }

    @Test
    void create_rejectsAnExplicitSlugAlreadyUsed_with409() throws Exception {
        bootcampRepository.save(TestData.bootcamp("pris", "Pris", dataBi));

        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Autre\",\"slug\":\"Pris\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce slug est déjà utilisé : pris"));
    }

    @Test
    void create_partnerDeliveredFormationRequiresAPartner() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"PSM I\",\"deliveredBy\":\"PARTNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Une formation dispensée par un partenaire doit avoir un partenaire (partnerId)."));

        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"PSM I\",\"deliveredBy\":\"PARTNER\",\"partnerId\":\"%s\"}"
                                .formatted(partner.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveredBy").value("PARTNER"))
                .andExpect(jsonPath("$.partnerId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.partner.name").value("Agile Sénégal"))
                .andExpect(jsonPath("$.partner.revenueSharePercent").doesNotExist());
    }

    @Test
    void create_unknownDomainOrPartner_is404() throws Exception {
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"domainId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"deliveredBy\":\"PARTNER\",\"partnerId\":\"%s\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_isPartial_keepsExistingCatalogueFields_andSwitchingToInternalClearsThePartner() throws Exception {
        Bootcamp b = TestData.bootcamp("formation", "Formation", dataBi);
        b.setDeliveredBy(com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy.PARTNER);
        b.setPartner(partner);
        b.setCertificationPrep("PSM I");
        b = bootcampRepository.save(b);

        // Mise à jour qui ne touche pas au catalogue : tout est conservé
        mockMvc.perform(put(URL + "/" + b.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Nouvelle description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("formation"))
                .andExpect(jsonPath("$.deliveredBy").value("PARTNER"))
                .andExpect(jsonPath("$.partnerId").value(partner.getId().toString()))
                .andExpect(jsonPath("$.certificationPrep").value("PSM I"))
                .andExpect(jsonPath("$.description").value("Nouvelle description"));

        mockMvc.perform(put(URL + "/" + b.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveredBy\":\"INTERNAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredBy").value("INTERNAL"))
                .andExpect(jsonPath("$.partner").doesNotExist());
    }

    @Test
    void update_changingTheTitleKeepsTheSlug_butAnExplicitSlugChangeIsValidated() throws Exception {
        Bootcamp b = bootcampRepository.save(TestData.bootcamp("stable", "Titre initial", dataBi));
        bootcampRepository.save(TestData.bootcamp("occupe", "Occupé", dataBi));

        mockMvc.perform(put(URL + "/" + b.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titre modifié\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("stable"));

        mockMvc.perform(put(URL + "/" + b.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"occupe\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put(URL + "/" + b.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"Nouveau Slug\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("nouveau-slug"));
    }

    @Test
    void update_replacesRelatedFormations_andRejectsSelfReference() throws Exception {
        Bootcamp a = bootcampRepository.save(TestData.bootcamp("a", "A", dataBi));
        Bootcamp b = bootcampRepository.save(TestData.bootcamp("b", "B", dataBi));

        mockMvc.perform(put(URL + "/" + a.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedFormationIds\":[\"%s\"]}".formatted(b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedFormationIds[0]").value(b.getId().toString()));

        mockMvc.perform(put(URL + "/" + a.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedFormationIds\":[\"%s\"]}".formatted(a.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(URL + "/" + a.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedFormationIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedFormationIds.length()").value(0));
    }

    @Test
    void detail_exposesTheCatalogueFields() throws Exception {
        Bootcamp b = TestData.bootcamp("detail", "Détail", dataBi);
        b = bootcampRepository.save(b);

        String json = mockMvc.perform(get(URL + "/" + b.getId()).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("slug").asText()).isEqualTo("detail");
        assertThat(node.get("domain").get("slug").asText()).isEqualTo("data-bi");
        assertThat(node.get("deliveredBy").asText()).isEqualTo("INTERNAL");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.local").roles("ADMIN");
    }
}
