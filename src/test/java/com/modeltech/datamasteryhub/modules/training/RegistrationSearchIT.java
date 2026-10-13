package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Liste des candidatures : étapes, recherche libre, filtres formation/session, pagination et tri côté serveur. */
class RegistrationSearchIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/registrations";
    private final RequestPostProcessor admissions = TestAuth.as("adm@test.local", "ADMISSIONS");

    @Autowired private ObjectMapper objectMapper;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;

    private Bootcamp powerBi;
    private Bootcamp excel;
    private BootcampSession sessionA;

    @BeforeEach
    void setUp() {
        powerBi = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        excel = bootcampRepository.save(TestData.bootcamp("excel", "Excel avancé", null));
        sessionA = sessionRepository.save(TestData.session(powerBi, "Cohorte A", LocalDate.now().plusDays(20), SessionStatus.OPEN));

        // 12 nouvelles, 3 en attente de paiement, 2 à confirmer, 4 inscrites, 3 archivées
        for (int i = 0; i < 12; i++) save("nouvelle" + i + "@example.com", "Nouvelle" + i, powerBi, sessionA, RegistrationStatus.PENDING);
        for (int i = 0; i < 3; i++) save("attente" + i + "@example.com", "Attente" + i, excel, null, RegistrationStatus.PAYMENT_PENDING);
        for (int i = 0; i < 2; i++) save("confirmer" + i + "@example.com", "Confirmer" + i, powerBi, sessionA, RegistrationStatus.PAYMENT_TO_CONFIRM);
        for (int i = 0; i < 3; i++) save("inscrit" + i + "@example.com", "Inscrit" + i, excel, null, RegistrationStatus.CONFIRMED);
        save("termine@example.com", "Termine", excel, null, RegistrationStatus.COMPLETED);
        save("refus@example.com", "Refus", powerBi, sessionA, RegistrationStatus.REJECTED);
        save("annule1@example.com", "Annule", powerBi, sessionA, RegistrationStatus.CANCELLED);
        save("annule2@example.com", "Annule", excel, null, RegistrationStatus.CANCELLED);
    }

    @Test
    void summary_countsEachStage_forTheSameSearch() throws Exception {
        JsonNode all = json(URL + "/summary", "");
        assertThat(all.get("NEW").asInt()).isEqualTo(12);
        assertThat(all.get("AWAITING_PAYMENT").asInt()).isEqualTo(3);
        assertThat(all.get("TO_CONFIRM").asInt()).isEqualTo(2);
        assertThat(all.get("ENROLLED").asInt()).isEqualTo(4);   // confirmées + terminées
        assertThat(all.get("ARCHIVED").asInt()).isEqualTo(3);   // refusées + annulées
        assertThat(all.get("ALL").asInt()).isEqualTo(24);

        JsonNode excelOnly = json(URL + "/summary", "?bootcampId=" + excel.getId());
        assertThat(excelOnly.get("NEW").asInt()).isZero();
        assertThat(excelOnly.get("ENROLLED").asInt()).isEqualTo(4);
        assertThat(excelOnly.get("ALL").asInt()).isEqualTo(3 + 4 + 1);
    }

    @Test
    void search_filtersByStage_paginates_andKeepsTheTotal() throws Exception {
        JsonNode page0 = json(URL + "/search", "?stage=NEW&size=5&page=0");
        assertThat(page0.get("totalElements").asInt()).isEqualTo(12);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.get("content").size()).isEqualTo(5);
        assertThat(json(URL + "/search", "?stage=NEW&size=5&page=2").get("content").size()).isEqualTo(2);

        JsonNode archived = json(URL + "/search", "?stage=ARCHIVED");
        assertThat(archived.get("totalElements").asInt()).isEqualTo(3);
        for (JsonNode r : archived.get("content")) assertThat(r.get("status").asText()).isIn("REJECTED", "CANCELLED");
        assertThat(json(URL + "/search", "?stage=ENROLLED").get("totalElements").asInt()).isEqualTo(4);
    }

    @Test
    void search_findsByNameEmailAndFormation_caseAndAccentInsensitiveOnCase_andIgnoresWildcards() throws Exception {
        assertThat(json(URL + "/search", "?q=NOUVELLE1").get("totalElements").asInt()).isEqualTo(3);          // Nouvelle1, 10, 11
        assertThat(json(URL + "/search", "?q=inscrit2@example.com").get("totalElements").asInt()).isEqualTo(1);
        assertThat(json(URL + "/search", "?q=excel").get("totalElements").asInt()).isEqualTo(3 + 4 + 1);      // par formation
        assertThat(json(URL + "/search", "?q=nouvelle%20attente").get("totalElements").asInt()).isZero();     // tous les mots doivent correspondre
        assertThat(json(URL + "/search", "?q=%25").get("totalElements").asInt()).as("le joker % est littéral").isZero();
    }

    @Test
    void search_combinesStageSessionAndFormation_andSortsOnAllowedFieldsOnly() throws Exception {
        assertThat(json(URL + "/search", "?stage=NEW&sessionId=" + sessionA.getId()).get("totalElements").asInt()).isEqualTo(12);
        assertThat(json(URL + "/search", "?stage=NEW&bootcampId=" + excel.getId()).get("totalElements").asInt()).isZero();

        JsonNode byName = json(URL + "/search", "?stage=NEW&sort=lastName,asc&size=3");
        assertThat(byName.at("/content/0/lastName").asText()).isEqualTo("Nouvelle0");
        // un champ de tri interdit ou inconnu ne provoque pas d'erreur serveur
        mockMvc.perform(get(URL + "/search?sort=passwordHash,asc").with(admissions)).andExpect(status().isOk());
        mockMvc.perform(get(URL + "/search?sort=nimporte,desc&size=100000").with(admissions)).andExpect(status().isOk());
        assertThat(json(URL + "/search", "?size=100000").get("size").asInt()).isLessThanOrEqualTo(200);
    }

    @Test
    void search_isReservedToProfilesThatViewApplications() throws Exception {
        mockMvc.perform(get(URL + "/search").with(TestAuth.as("t@test.local", "TRAINER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/summary").with(TestAuth.as("c@test.local", "COMMUNICATION"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/search").with(TestAuth.as("f@test.local", "FINANCE"))).andExpect(status().isOk());
    }

    private Registration save(String email, String lastName, Bootcamp bootcamp, BootcampSession session, RegistrationStatus status) {
        Registration r = TestData.registration(email, bootcamp, session, null);
        r.setLastName(lastName);
        r.setStatus(status);
        return registrationRepository.save(r);
    }

    private JsonNode json(String path, String query) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path + query).with(admissions)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
