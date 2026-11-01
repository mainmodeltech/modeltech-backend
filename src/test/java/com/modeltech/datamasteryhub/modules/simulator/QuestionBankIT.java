package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Banque de questions du simulateur : blueprint, rédaction, relecture à deux, publication, versionnage, import, droits. */
class QuestionBankIT extends AbstractIntegrationTest {

    private static final String API = "/api/v1/admin/simulator";
    private static final String EXPLANATION = "Un filtre booléen de CALCULATE remplace le filtre existant sur la colonne.";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuditRepository auditRepository;

    private static RequestPostProcessor author() {
        return TestAuth.as("auteur@test.local", "PEDAGOGY");
    }

    private static RequestPostProcessor reviewer() {
        return TestAuth.as("relecteur@test.local", "PEDAGOGY");
    }

    private static String singleChoice(String skill, String stem, boolean withExplanation) {
        return """
                {"skillCode":"%s","type":"SINGLE_CHOICE","difficulty":2,"stemEn":"%s","explanationFr":%s,
                 "payload":{"choices":[{"id":"A","text":"CALCULATE","correct":true},{"id":"B","text":"SUMX","correct":false},
                                       {"id":"C","text":"FILTER","correct":false},{"id":"D","text":"VALUES","correct":false}]}}"""
                .formatted(skill, stem, withExplanation ? "\"" + EXPLANATION + "\"" : "null");
    }

    private JsonNode create(String skill) throws Exception {
        return json(mockMvc.perform(post(API + "/items").with(author()).contentType(MediaType.APPLICATION_JSON)
                .content(singleChoice(skill, "Which function changes the filter context?", true))).andExpect(status().isCreated()));
    }

    private void send(String path, RequestPostProcessor who, String body, int expected) throws Exception {
        mockMvc.perform(post(API + path).with(who).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }

    // ── Blueprint ────────────────────────────────────────────────────

    @Test
    void theShippedBlueprintDescribesThePl300SkillsMeasured() throws Exception {
        JsonNode bp = json(mockMvc.perform(get(API + "/blueprint").with(author())).andExpect(status().isOk())).at("/data");
        assertThat(bp.at("/certification").asText()).isEqualTo("PL-300");
        assertThat(bp.at("/versionDate").asText()).isEqualTo("2026-04-20");
        assertThat(bp.at("/passScore").asInt()).isEqualTo(700);
        assertThat(bp.at("/durationMinutes").asInt()).isEqualTo(100);
        assertThat(bp.at("/domains")).hasSize(4);
        assertThat(bp.at("/domains/3/code").asText()).isEqualTo("SEC");
        assertThat(bp.at("/domains/3/weightMin").asInt()).isEqualTo(15);
        int target = 0;
        for (JsonNode d : bp.at("/domains")) for (JsonNode s : d.at("/skills")) target += s.at("/targetQuestions").asInt();
        assertThat(target).isEqualTo(400);
    }

    // ── Rédaction ────────────────────────────────────────────────────

    @Test
    void anAuthorCreatesADraft_withASequentialCode_andInvalidQuestionsAreRefused() throws Exception {
        JsonNode first = create("MOD-DAX").at("/data");
        assertThat(first.at("/code").asText()).isEqualTo("PL300-MOD-001");
        assertThat(first.at("/status").asText()).isEqualTo("DRAFT");
        assertThat(first.at("/versionNo").asInt()).isEqualTo(1);
        assertThat(create("MOD-DESIGN").at("/data/code").asText()).isEqualTo("PL300-MOD-002");
        assertThat(create("SEC-SECURITY").at("/data/code").asText()).isEqualTo("PL300-SEC-001");

        // deux bonnes réponses sur un choix unique
        send("/items", author(), """
                {"skillCode":"MOD-DAX","type":"SINGLE_CHOICE","difficulty":1,"stemEn":"x",
                 "payload":{"choices":[{"id":"A","text":"a","correct":true},{"id":"B","text":"b","correct":true}]}}""", 400);
        // compétence inconnue, difficulté hors bornes, énoncé vide
        send("/items", author(), singleChoice("NOPE", "stem", true), 400);
        send("/items", author(), singleChoice("MOD-DAX", "stem", true).replace("\"difficulty\":2", "\"difficulty\":9"), 400);
        send("/items", author(), singleChoice("MOD-DAX", " ", true), 400);
    }

    @Test
    void onlyTheAuthorEditsADraft_andAWrongSkillChangeIsRefused() throws Exception {
        String code = create("MOD-DAX").at("/data/code").asText();
        String edit = singleChoice("MOD-DAX", "Updated stem", true);

        mockMvc.perform(put(API + "/items/" + code + "/versions/1").with(reviewer()).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(API + "/items/" + code + "/versions/1").with(author()).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stemEn").value("Updated stem"));
        mockMvc.perform(put(API + "/items/" + code + "/versions/1").with(author()).contentType(MediaType.APPLICATION_JSON)
                .content(singleChoice("SEC-SECURITY", "Updated stem", true))).andExpect(status().isBadRequest());
    }

    // ── Relecture, publication, versionnage ─────────────────────────

    @Test
    void fullWorkflow_twoPeopleAreNeeded_andAPublishedVersionIsFrozen() throws Exception {
        String code = create("MOD-DAX").at("/data/code").asText();
        String v = "/items/" + code + "/versions/1";

        // explication obligatoire avant la relecture
        String noExplanation = json(mockMvc.perform(post(API + "/items").with(author()).contentType(MediaType.APPLICATION_JSON)
                .content(singleChoice("MOD-DAX", "Other question", false))).andExpect(status().isCreated())).at("/data/code").asText();
        send("/items/" + noExplanation + "/versions/1/submit", author(), "{}", 400);

        send(v + "/submit", author(), "{}", 200);
        send(v + "/submit", author(), "{}", 409);   // déjà en relecture
        // l'auteur ne relit pas sa propre question
        send(v + "/review", author(), "{\"approve\":true,\"verified\":true}", 403);
        // sans attestation de vérification technique
        send(v + "/review", reviewer(), "{\"approve\":true,\"verified\":false}", 400);
        send(v + "/review", reviewer(), "{\"approve\":true,\"verified\":true,\"note\":\"Exécuté dans Power BI\"}", 200);
        // l'auteur ne publie pas sa propre question
        send(v + "/publish", author(), "{}", 403);
        mockMvc.perform(post(API + v + "/publish").with(reviewer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED")).andExpect(jsonPath("$.data.publisher").value("relecteur@test.local"));

        // figée : ni modification ni suppression
        mockMvc.perform(put(API + "/items/" + code + "/versions/1").with(author()).contentType(MediaType.APPLICATION_JSON)
                .content(singleChoice("MOD-DAX", "Sneaky edit", true))).andExpect(status().isConflict());
        mockMvc.perform(delete(API + v).with(author())).andExpect(status().isConflict());

        // les décisions sensibles sont dans l'audit
        assertThat(auditRepository.findAll()).extracting(a -> a.getAction())
                .contains("SIM_ITEM_APPROVED", "SIM_ITEM_PUBLISHED");

        // nouvelle version : un brouillon v2 ; une seule version en cours à la fois
        mockMvc.perform(post(API + "/items/" + code + "/versions").with(author())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNo").value(2)).andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(post(API + "/items/" + code + "/versions").with(author())).andExpect(status().isConflict());

        String v2 = "/items/" + code + "/versions/2";
        send(v2 + "/submit", author(), "{}", 200);
        send(v2 + "/review", reviewer(), "{\"approve\":true,\"verified\":true}", 200);
        // publier v2 retire automatiquement v1 (une seule version publiée)
        mockMvc.perform(post(API + v2 + "/publish").with(reviewer())).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get(API + "/items/" + code).param("version", "1").with(author()))).at("/data/status").asText())
                .isEqualTo("RETIRED");
        assertThat(json(mockMvc.perform(get(API + "/items/" + code).with(author()))).at("/data/versions")).hasSize(2);

        // une version retirée ne se retire plus ; retrait avec motif obligatoire
        send(v + "/retire", reviewer(), "{\"reason\":\"x\"}", 409);
        send(v2 + "/retire", reviewer(), "{\"reason\":\" \"}", 400);
        send(v2 + "/retire", reviewer(), "{\"reason\":\"Fonction obsolète\"}", 200);

        // la base refuse aussi toute modification d'une version figée (dernière instruction : l'erreur annule la transaction du test)
        assertThatThrownBy(() -> jdbc.update("UPDATE sim_item_version SET stem_en = 'hacked' WHERE version_no = 1"))
                .hasMessageMatching("(?s).*(figee|retiree).*");
    }

    @Test
    void changesRequested_sendsTheQuestionBackToItsAuthor() throws Exception {
        String code = create("MOD-DAX").at("/data/code").asText();
        String v1 = "/items/" + code + "/versions/1";
        send(v1 + "/submit", author(), "{}", 200);
        send(v1 + "/review", reviewer(), "{\"approve\":false}", 400);   // note obligatoire
        send(v1 + "/review", reviewer(), "{\"approve\":false,\"note\":\"Le choix C est aussi défendable\"}", 200);

        mockMvc.perform(get(API + "/items/" + code).with(author())).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.reviewNote").value("Le choix C est aussi défendable"));
        // l'auteur corrige puis soumet de nouveau
        mockMvc.perform(put(API + v1).with(author()).contentType(MediaType.APPLICATION_JSON)
                .content(singleChoice("MOD-DAX", "Rewritten", true))).andExpect(status().isOk());
        send(v1 + "/submit", author(), "{}", 200);
    }

    @Test
    void aDraftCanBeDeletedByItsAuthor_andTheEmptyItemDisappears() throws Exception {
        String code = create("MOD-DAX").at("/data/code").asText();
        mockMvc.perform(delete(API + "/items/" + code + "/versions/1").with(reviewer())).andExpect(status().isForbidden());
        mockMvc.perform(delete(API + "/items/" + code + "/versions/1").with(author())).andExpect(status().isNoContent());
        mockMvc.perform(get(API + "/items/" + code).with(author())).andExpect(status().isNotFound());
    }

    // ── Liste, couverture, import ───────────────────────────────────

    @Test
    void searchAndCoverageFollowTheProduction() throws Exception {
        String a = create("MOD-DAX").at("/data/code").asText();
        create("MOD-DAX");
        create("SEC-SECURITY");
        send("/items/" + a + "/versions/1/submit", author(), "{}", 200);

        assertThat(json(mockMvc.perform(get(API + "/items").with(author()))).at("/data")).hasSize(3);
        assertThat(json(mockMvc.perform(get(API + "/items").param("status", "IN_REVIEW").with(author()))).at("/data")).hasSize(1);
        assertThat(json(mockMvc.perform(get(API + "/items").param("domain", "SEC").with(author()))).at("/data")).hasSize(1);
        assertThat(json(mockMvc.perform(get(API + "/items").param("skill", "MOD-DAX").param("q", "filter").with(author()))).at("/data")).hasSize(2);
        assertThat(json(mockMvc.perform(get(API + "/items").param("mine", "true").with(reviewer()))).at("/data")).isEmpty();

        JsonNode coverage = json(mockMvc.perform(get(API + "/coverage").with(author()))).at("/data");
        assertThat(coverage).hasSize(11);
        for (JsonNode row : coverage) {
            if ("MOD-DAX".equals(row.at("/skillCode").asText())) {
                assertThat(row.at("/target").asInt()).isEqualTo(56);
                assertThat(row.at("/draft").asInt()).isEqualTo(1);
                assertThat(row.at("/inReview").asInt()).isEqualTo(1);
            }
        }
    }

    @Test
    void importCreatesDrafts_reportsEachRefusedQuestion_andNeverPublishes() throws Exception {
        String good = singleChoice("PRE-TRANSFORM", "Which transformation turns columns into rows?", true);
        String bad = singleChoice("PRE-TRANSFORM", "Bad one", true).replace("\"correct\":true", "\"correct\":false");
        JsonNode result = json(mockMvc.perform(post(API + "/items/import").with(author()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + good + "," + bad + "," + good + "]}")).andExpect(status().isOk())).at("/data");
        assertThat(result.at("/created").asInt()).isEqualTo(2);
        assertThat(result.at("/codes/0").asText()).isEqualTo("PL300-PRE-001");
        assertThat(result.at("/errors/0/index").asInt()).isEqualTo(1);
        assertThat(result.at("/errors/0/message").asText()).contains("bonne réponse");
        assertThat(json(mockMvc.perform(get(API + "/items").param("status", "DRAFT").with(author()))).at("/data")).hasSize(2);

        // un auteur sans droit de gestion ne peut pas importer (le profil Formateur n'a aucun droit de simulateur)
        mockMvc.perform(post(API + "/items/import").with(TestAuth.as("f@test.local", "TRAINER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + good + "]}")).andExpect(status().isForbidden());
    }

    // ── Droits ───────────────────────────────────────────────────────

    @Test
    void theBankIsRestrictedToTheProfilesThatHoldTheSimulatorRights() throws Exception {
        for (String profile : new String[]{"ADMISSIONS", "FINANCE", "COMMUNICATION", "TRAINER", "PARTNER", "AUDITOR"}) {
            mockMvc.perform(get(API + "/items").with(TestAuth.as("x@test.local", profile))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(API + "/items").with(TestAuth.as("d@test.local", "SUPER_ADMIN"))).andExpect(status().isOk());
        // lecture seule : voit mais n'écrit pas ni ne publie
        RequestPostProcessor readOnly = TestAuth.withPermissions("ro@test.local", com.modeltech.datamasteryhub.modules.auth.permission.Permission.SIMULATOR_VIEW);
        mockMvc.perform(get(API + "/items").with(readOnly)).andExpect(status().isOk());
        mockMvc.perform(post(API + "/items").with(readOnly).contentType(MediaType.APPLICATION_JSON)
                .content(singleChoice("MOD-DAX", "x", true))).andExpect(status().isForbidden());
        // l'auteur pur ne relit ni ne publie
        RequestPostProcessor authorOnly = TestAuth.withPermissions("ao@test.local",
                com.modeltech.datamasteryhub.modules.auth.permission.Permission.SIMULATOR_VIEW,
                com.modeltech.datamasteryhub.modules.auth.permission.Permission.SIMULATOR_AUTHOR);
        mockMvc.perform(post(API + "/items/PL300-MOD-001/versions/1/review").with(authorOnly).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":true,\"verified\":true}")).andExpect(status().isForbidden());
        mockMvc.perform(post(API + "/items/PL300-MOD-001/versions/1/publish").with(authorOnly)).andExpect(status().isForbidden());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
