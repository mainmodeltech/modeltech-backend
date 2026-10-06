package com.modeltech.datamasteryhub.modules.training;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/domains et GET /api/v1/partners (publics). */
class PublicDomainAndPartnerControllerIT extends AbstractIntegrationTest {

    @Autowired private DomainRepository domainRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private BootcampRepository bootcampRepository;

    @Test
    void domains_areListedInDisplayOrder_withTheSeededDataBiDomainFirst() throws Exception {
        Domain agile = TestData.domain("gestion-projet", "Gestion de projet & Agile", true);
        agile.setDisplayOrder(1);
        agile.setBadge("NOUVEAU · EN PARTENARIAT");
        domainRepository.save(agile);
        Domain soon = TestData.domain("cybersecurite", "Cybersécurité", true);
        soon.setDisplayOrder(2);
        soon.setComingSoon(true);
        domainRepository.save(soon);

        mockMvc.perform(get("/api/v1/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(contains("data-bi", "gestion-projet", "cybersecurite")))
                .andExpect(jsonPath("$[0].name").value("Data & BI"))
                .andExpect(jsonPath("$[0].badge").value("NOTRE SPÉCIALITÉ"))
                .andExpect(jsonPath("$[0].comingSoon").value(false))
                .andExpect(jsonPath("$[2].comingSoon").value(true))
                .andExpect(jsonPath("$[1].displayOrder").value(1))
                .andExpect(jsonPath("$[1].visible").doesNotExist());
    }

    @Test
    void domains_excludeHiddenAndDeletedOnes() throws Exception {
        domainRepository.save(TestData.domain("cache", "Caché", false));
        Domain deleted = TestData.domain("supprime", "Supprimé", true);
        deleted.setDeleted(true);
        domainRepository.save(deleted);

        mockMvc.perform(get("/api/v1/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(not(hasItem("cache"))))
                .andExpect(jsonPath("$[*].slug").value(not(hasItem("supprime"))));
    }

    @Test
    void partners_onlyThoseWithAPublishedFormation_andNeverTheInternalFields() throws Exception {
        Domain domain = domainRepository.findAll().get(0);
        Partner withFormation = partnerRepository.save(TestData.partner("agile-senegal", "Agile Sénégal"));
        partnerRepository.save(TestData.partner("sans-formation", "Sans formation"));
        Partner onlyDraft = partnerRepository.save(TestData.partner("brouillon", "Seulement brouillon"));

        Bootcamp published = TestData.bootcamp("psm", "PSM", domain);
        published.setDeliveredBy(DeliveredBy.PARTNER);
        published.setPartner(withFormation);
        bootcampRepository.save(published);

        Bootcamp draft = TestData.bootcamp("draft", "Draft", domain);
        draft.setDeliveredBy(DeliveredBy.PARTNER);
        draft.setPartner(onlyDraft);
        draft.setPublished(false);
        bootcampRepository.save(draft);

        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("agile-senegal"))
                .andExpect(jsonPath("$[0].name").value("Agile Sénégal"))
                .andExpect(jsonPath("$[0].bio").value("Bio de Agile Sénégal"))
                .andExpect(jsonPath("$[0].website").value("https://example.com/agile-senegal"))
                .andExpect(jsonPath("$[0].revenueSharePercent").doesNotExist())
                .andExpect(jsonPath("$[0].contactEmail").doesNotExist())
                .andExpect(jsonPath("$[0].active").doesNotExist());
    }

    @Test
    void partners_inactivePartnersAreHidden() throws Exception {
        Domain domain = domainRepository.findAll().get(0);
        Partner inactive = TestData.partner("inactif", "Inactif");
        inactive.setActive(false);
        inactive = partnerRepository.save(inactive);
        Bootcamp b = TestData.bootcamp("formation-inactif", "Formation", domain);
        b.setDeliveredBy(DeliveredBy.PARTNER);
        b.setPartner(inactive);
        bootcampRepository.save(b);

        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
