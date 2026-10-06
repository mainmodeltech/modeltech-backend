package com.modeltech.datamasteryhub.modules.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.FormationFormat;
import com.modeltech.datamasteryhub.modules.training.enums.FormationLevel;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.DomainRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/formations, /formations/slug/{slug}, /formations/sessions, /domains, /partners (public). */
class PublicFormationControllerIT extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private DomainRepository domainRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;

    private Domain dataBi;          // domaine créé par la migration V18 (Q7)
    private Domain agile;
    private Bootcamp powerBi;
    private Bootcamp python;
    private Bootcamp psm;

    @BeforeEach
    void setUp() {
        dataBi = domainRepository.findAll().stream()
                .filter(d -> "data-bi".equals(d.getSlug())).findFirst().orElseThrow();
        agile = domainRepository.save(TestData.domain("gestion-projet", "Gestion de projet & Agile", true));
        Domain hidden = domainRepository.save(TestData.domain("cache", "Domaine caché", false));
        Partner partner = partnerRepository.save(TestData.partner("agile-senegal", "Agile Sénégal"));

        powerBi = TestData.bootcamp("power-bi", "Power BI", dataBi);
        powerBi.setLevel(FormationLevel.DEBUTANT);
        powerBi.setFormat(FormationFormat.PRESENTIEL);
        powerBi.setTargetRoles(new ArrayList<>(List.of("Finance", "Audit")));
        powerBi = bootcampRepository.save(powerBi);

        python = bootcampRepository.save(TestData.bootcamp("python", "Python pour la data", dataBi));

        psm = TestData.bootcamp("psm-1", "Scrum Master PSM I", agile);
        psm.setDeliveredBy(DeliveredBy.PARTNER);
        psm.setPartner(partner);
        psm.setLevel(FormationLevel.INTERMEDIAIRE);
        psm.setFormat(FormationFormat.HYBRIDE);
        psm.setProfiles(null);          // colonnes JSONB nulles en base : ne doivent pas être omises
        psm.setTools(null);
        psm.setTargetRoles(null);
        psm = bootcampRepository.save(psm);

        // Exclus du catalogue : non publiée, sans domaine, domaine caché
        Bootcamp draft = TestData.bootcamp("brouillon", "Brouillon", dataBi);
        draft.setPublished(false);
        bootcampRepository.save(draft);
        bootcampRepository.save(TestData.bootcamp("sans-domaine", "Sans domaine", null));
        bootcampRepository.save(TestData.bootcamp("domaine-cache", "Domaine caché", hidden));

        sessionRepository.save(TestData.session(powerBi, "Cohorte 2", LocalDate.of(2030, 2, 10), SessionStatus.OPEN));
        sessionRepository.save(TestData.session(powerBi, "Cohorte 1", LocalDate.of(2030, 1, 5), SessionStatus.UPCOMING));
        sessionRepository.save(TestData.session(powerBi, "Terminée", LocalDate.of(2020, 1, 1), SessionStatus.CLOSED));
        sessionRepository.save(TestData.session(powerBi, "Brouillon", LocalDate.of(2030, 3, 1), SessionStatus.DRAFT));
        sessionRepository.save(TestData.session(psm, "PSM Cohorte 1", LocalDate.of(2030, 1, 20), SessionStatus.OPEN));
    }

    // ── GET /formations ──────────────────────────────────────────────

    @Test
    void list_isPublic_andExcludesUnpublishedWithoutDomainAndHiddenDomain() throws Exception {
        JsonNode formations = getJson("/api/v1/formations");

        assertThat(slugs(formations)).containsExactlyInAnyOrder("power-bi", "python", "psm-1");
    }

    @Test
    void list_exposesTheFormationContract() throws Exception {
        JsonNode bi = bySlug(getJson("/api/v1/formations"), "power-bi");

        assertThat(bi.get("id").asText()).isEqualTo(powerBi.getId().toString());
        assertThat(bi.get("domainId").asText()).isEqualTo(dataBi.getId().toString());
        assertThat(bi.get("domain").get("slug").asText()).isEqualTo("data-bi");
        assertThat(bi.get("domain").get("name").asText()).isEqualTo("Data & BI");
        assertThat(bi.get("domain").get("comingSoon").asBoolean()).isFalse();
        assertThat(bi.get("deliveredBy").asText()).isEqualTo("INTERNAL");
        assertThat(bi.get("level").asText()).isEqualTo("DEBUTANT");
        assertThat(bi.get("format").asText()).isEqualTo("PRESENTIEL");
        assertThat(bi.get("targetRoles")).extracting(JsonNode::asText).containsExactly("Finance", "Audit");
        assertThat(bi.has("partner")).isFalse();
    }

    @Test
    void list_neverOmitsCollectionsDeclaredNonNullByTheFrontContract() throws Exception {
        JsonNode formation = bySlug(getJson("/api/v1/formations"), "psm-1");

        for (String field : List.of("benefits", "targetRoles", "profiles", "tools", "curriculum",
                "outcomes", "sessions", "relatedFormationIds")) {
            assertThat(formation.has(field)).as(field).isTrue();
            assertThat(formation.get(field).isArray()).as(field).isTrue();
        }
    }

    @Test
    void list_includesSessionsAndNextSession() throws Exception {
        JsonNode bi = bySlug(getJson("/api/v1/formations"), "power-bi");

        // Toutes les sessions publiées (4) ; nextSession = prochaine session OPEN/UPCOMING
        // (et non la session terminée de 2020, pourtant la 1re par date de début)
        assertThat(bi.get("sessions")).hasSize(4);
        assertThat(bi.get("nextSession").get("sessionName").asText()).isEqualTo("Cohorte 1");
    }

    @Test
    void list_relatedFormationsFallBackToSameDomain() throws Exception {
        JsonNode formations = getJson("/api/v1/formations");

        assertThat(bySlug(formations, "power-bi").get("relatedFormationIds"))
                .extracting(JsonNode::asText).containsExactly(python.getId().toString());
        // Seule formation de son domaine : aucune formation liée
        assertThat(bySlug(formations, "psm-1").get("relatedFormationIds")).isEmpty();
    }

    @Test
    void list_partnerFormationExposesPartnerWithoutInternalFields() throws Exception {
        mockMvc.perform(get("/api/v1/formations").param("deliveredBy", "PARTNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("psm-1"))
                .andExpect(jsonPath("$[0].partner.name").value("Agile Sénégal"))
                .andExpect(jsonPath("$[0].partner.website").value("https://example.com/agile-senegal"))
                .andExpect(jsonPath("$[0].partner.revenueSharePercent").doesNotExist())
                .andExpect(jsonPath("$[0].partner.contactEmail").doesNotExist());
    }

    @Test
    void list_filtersByDomainLevelFormatAndTargetRole() throws Exception {
        assertThat(slugs(getJson("/api/v1/formations?domain=data-bi")))
                .containsExactlyInAnyOrder("power-bi", "python");
        assertThat(slugs(getJson("/api/v1/formations?level=INTERMEDIAIRE"))).containsExactly("psm-1");
        assertThat(slugs(getJson("/api/v1/formations?format=PRESENTIEL"))).containsExactly("power-bi");
        assertThat(slugs(getJson("/api/v1/formations?targetRole=finance"))).containsExactly("power-bi");
        assertThat(slugs(getJson("/api/v1/formations?domain=data-bi&level=INTERMEDIAIRE"))).isEmpty();
    }

    @Test
    void list_rejectsAnUnknownEnumFilterWith400() throws Exception {
        mockMvc.perform(get("/api/v1/formations").param("level", "EXPERT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valeur invalide pour le paramètre « level »"));
    }

    // ── GET /formations/slug/{slug} ──────────────────────────────────

    @Test
    void bySlug_returnsTheFormation() throws Exception {
        mockMvc.perform(get("/api/v1/formations/slug/power-bi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Power BI"))
                .andExpect(jsonPath("$.domain.slug").value("data-bi"))
                .andExpect(jsonPath("$.sessions.length()").value(4));
    }

    @Test
    void bySlug_isNotFoundForUnknownUnpublishedOrDomainlessFormations() throws Exception {
        for (String slug : List.of("inconnu", "brouillon", "sans-domaine", "domaine-cache")) {
            mockMvc.perform(get("/api/v1/formations/slug/" + slug))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    // ── GET /formations/sessions ─────────────────────────────────────

    @Test
    void sessions_listsOnlyOpenAndUpcomingSortedByStartDateWithFormationInfo() throws Exception {
        JsonNode sessions = getJson("/api/v1/formations/sessions");

        assertThat(StreamSupport.stream(sessions.spliterator(), false)
                .map(s -> s.get("sessionName").asText()))
                .containsExactly("Cohorte 1", "PSM Cohorte 1", "Cohorte 2");

        JsonNode partnerSession = sessions.get(1);
        assertThat(partnerSession.get("formationId").asText()).isEqualTo(psm.getId().toString());
        assertThat(partnerSession.get("formationSlug").asText()).isEqualTo("psm-1");
        assertThat(partnerSession.get("formationTitle").asText()).isEqualTo("Scrum Master PSM I");
        assertThat(partnerSession.get("domainName").asText()).isEqualTo("Gestion de projet & Agile");
        assertThat(partnerSession.get("deliveredBy").asText()).isEqualTo("PARTNER");
        assertThat(partnerSession.get("partnerName").asText()).isEqualTo("Agile Sénégal");
        assertThat(partnerSession.get("bootcampId").asText()).isEqualTo(psm.getId().toString());
        assertThat(partnerSession.has("spotsRemaining")).isTrue();
    }

    // ── Non-régression : le catalogue historique /bootcamps ──────────

    @Test
    void legacyBootcampsEndpoint_stillWorksAndExposesTheNewFieldsAdditively() throws Exception {
        mockMvc.perform(get("/api/v1/bootcamps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='power-bi')].title").value("Power BI"))
                .andExpect(jsonPath("$[?(@.slug=='power-bi')].nextSession").exists());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private JsonNode getJson(String url) throws Exception {
        String body = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private List<String> slugs(JsonNode formations) {
        return StreamSupport.stream(formations.spliterator(), false)
                .map(f -> f.get("slug").asText()).toList();
    }

    private JsonNode bySlug(JsonNode formations, String slug) {
        return StreamSupport.stream(formations.spliterator(), false)
                .filter(f -> slug.equals(f.get("slug").asText())).findFirst().orElseThrow();
    }
}
