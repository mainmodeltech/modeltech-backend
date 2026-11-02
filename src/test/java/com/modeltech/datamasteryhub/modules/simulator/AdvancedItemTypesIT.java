package com.modeltech.datamasteryhub.modules.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.simulator.entity.*;
import com.modeltech.datamasteryhub.modules.simulator.repository.*;
import com.modeltech.datamasteryhub.modules.simulator.strategy.ItemTypeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Correspondance (glisser-déposer) et réponse libre à mots-clés : de la rédaction à la correction, sans fuite du corrigé. */
class AdvancedItemTypesIT extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/simulator";
    private static final String LEARNER = "/api/v1/learner/simulator";
    private static final String SECRET = "EXPLICATION-SECRETE-TYPES";

    private static final String MATCHING = """
            {"skillCode":"MOD-DAX","type":"MATCHING","difficulty":2,"stemEn":"Match each DAX function to what it does.","explanationFr":"%s",
             "payload":{"prompts":[{"id":"P1","text":"SUM"},{"id":"P2","text":"CALCULATE"},{"id":"P3","text":"RELATED"}],
                        "options":[{"id":"O1","text":"Adds the values of a column"},{"id":"O2","text":"Changes the filter context"},
                                   {"id":"O3","text":"Reads a value from a related table"},{"id":"O4","text":"Sorts a table"}],
                        "pairs":{"P1":"O1","P2":"O2","P3":"O3"}}}""".formatted(SECRET);

    private static final String SHORT = """
            {"skillCode":"MOD-DAX","type":"SHORT_ANSWER","difficulty":2,"stemEn":"Explain why a measure is preferred to a calculated column.","explanationFr":"%s",
             "payload":{"keywords":[{"id":"K1","accept":["measure","mesure"]},{"id":"K2","accept":["filter context","contexte de filtre"]}]}}""".formatted(SECRET);

    @Autowired private ObjectMapper objectMapper;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private SimBlueprintRepository blueprints;
    @Autowired private SimItemRepository items;
    @Autowired private SimItemVersionRepository versions;
    @Autowired private ItemTypeRegistry registry;

    private static RequestPostProcessor author() {
        return TestAuth.as("auteur@test.local", "PEDAGOGY");
    }

    private static RequestPostProcessor as(String email) {
        return TestAuth.as(email, "LEARNER");
    }

    private ResultActions post(String path, RequestPostProcessor who, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ── Rédaction ────────────────────────────────────────────────────

    @Test
    void bothTypesCanBeAuthored_andInvalidDefinitionsAreRefusedWithAClearMessage() throws Exception {
        assertThat(json(post(ADMIN + "/items", author(), MATCHING).andExpect(status().isCreated())).at("/data/type").asText()).isEqualTo("MATCHING");
        JsonNode created = json(post(ADMIN + "/items", author(), SHORT).andExpect(status().isCreated())).at("/data");
        assertThat(created.at("/type").asText()).isEqualTo("SHORT_ANSWER");
        assertThat(created.at("/payload/maxLength").asInt()).isEqualTo(600);          // valeur par défaut posée par le serveur
        assertThat(created.at("/payload/partialCredit").asBoolean()).isFalse();

        // un élément sans option attendue
        String broken = MATCHING.replace("\"P3\":\"O3\"", "\"P3\":\"O9\"");
        String message = json(post(ADMIN + "/items", author(), broken).andExpect(status().isBadRequest())).toString();
        assertThat(message).contains("P3");
        // aucun mot-clé
        post(ADMIN + "/items", author(), SHORT.replaceAll("\"keywords\":\\[.*\\]\\}\\}", "\"keywords\":[]}}")).andExpect(status().isBadRequest());
    }

    // ── Tentative ────────────────────────────────────────────────────

    @Test
    void inStudyMode_theCorrectionIsImmediate_andNothingLeaksBeforeAnswering() throws Exception {
        SimBlueprint bp = blueprints.findFirstByCertificationCodeAndActiveTrueOrderByVersionDateDesc("PL-300").orElseThrow();
        SimBlueprintSkill skill = bp.getDomains().stream().flatMap(d -> d.getSkills().stream()).filter(s -> s.getCode().equals("MOD-DAX")).findFirst().orElseThrow();
        publish(bp, skill, "PL300-A-1", ItemType.MATCHING, Map.of(
                "prompts", java.util.List.of(Map.of("id", "P1", "text", "SUM"), Map.of("id", "P2", "text", "CALCULATE")),
                "options", java.util.List.of(Map.of("id", "O1", "text", "Adds values"), Map.of("id", "O2", "text", "Changes the filter context"), Map.of("id", "O3", "text", "Sorts")),
                "pairs", Map.of("P1", "O1", "P2", "O2")));
        publish(bp, skill, "PL300-A-2", ItemType.SHORT_ANSWER, Map.of(
                "keywords", java.util.List.of(Map.of("id", "K1", "accept", java.util.List.of("measure")), Map.of("id", "K2", "accept", java.util.List.of("filter context")))));

        Learner l = new Learner();
        l.setEmail("awa@test.local");
        l.setFirstName("Awa");
        l.setLastName("Diop");
        learnerRepository.save(l);
        post(ADMIN + "/grants", TestAuth.as("pedago@test.local", "PEDAGOGY"),
                "{\"learnerEmail\":\"awa@test.local\",\"plan\":\"PRO\",\"days\":30,\"examAttempts\":1,\"reason\":\"test\"}").andExpect(status().isCreated());

        String startBody = post(LEARNER + "/attempts", as("awa@test.local"), "{\"mode\":\"STUDY\",\"domain\":\"MOD\",\"count\":2}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(startBody).doesNotContain("pairs").doesNotContain("keywords").doesNotContain("accept").doesNotContain("K1").doesNotContain(SECRET);
        JsonNode attempt = objectMapper.readTree(startBody).at("/data");
        String id = attempt.at("/id").asText();

        for (JsonNode q : attempt.at("/questions")) {
            String ref = q.at("/ref").asText();
            if (q.at("/type").asText().equals("MATCHING")) {
                assertThat(q.at("/payload/prompts")).hasSize(2);
                assertThat(q.at("/payload/options")).hasSize(3);
                // une option placée deux fois est refusée (réutilisation désactivée)
                put(id, ref, "{\"answer\":{\"pairs\":{\"P1\":\"O1\",\"P2\":\"O1\"}}}").andExpect(status().isBadRequest());
                // une moitié juste : note 0,5 et la bonne réponse arrive avec la correction
                JsonNode fb = json(put(id, ref, "{\"answer\":{\"pairs\":{\"P1\":\"O1\",\"P2\":\"O3\"}}}").andExpect(status().isOk())).at("/data/feedback");
                assertThat(fb.at("/score").asDouble()).isEqualTo(0.5);
                assertThat(fb.at("/correctAnswer/pairs/P2").asText()).isEqualTo("O2");
                assertThat(fb.at("/explanationFr").asText()).isEqualTo(SECRET);
            } else {
                assertThat(q.at("/payload").toString()).isEqualTo("{\"maxLength\":600}");
                JsonNode fb = json(put(id, ref, "{\"answer\":{\"text\":\"Use a MEASURE.\"}}").andExpect(status().isOk())).at("/data/feedback");
                assertThat(fb.at("/score").asDouble()).isZero();                      // « filter context » manque
                assertThat(fb.at("/detail/found").toString()).isEqualTo("[\"K1\"]");
                assertThat(fb.at("/detail/missing").toString()).isEqualTo("[\"K2\"]");
                put(id, ref, "{\"answer\":{\"text\":\"" + "x".repeat(601) + "\"}}").andExpect(status().isBadRequest());
                JsonNode ok = json(put(id, ref, "{\"answer\":{\"text\":\"A measure reacts to the filter context\"}}").andExpect(status().isOk())).at("/data/feedback");
                assertThat(ok.at("/score").asDouble()).isEqualTo(1.0);
            }
        }

        JsonNode report = json(post(LEARNER + "/attempts/" + id + "/submit", as("awa@test.local"), "").andExpect(status().isOk())).at("/data");
        assertThat(report.at("/questions")).hasSize(2);
        for (JsonNode q : report.at("/questions")) {
            assertThat(q.at("/explanationFr").asText()).isEqualTo(SECRET);
            if (q.at("/type").asText().equals("SHORT_ANSWER")) {
                assertThat(q.at("/detail/missing")).isEmpty();
                assertThat(q.at("/correctAnswer/keywords/0/accept/0").asText()).isEqualTo("measure");
            }
        }
    }

    private ResultActions put(String attemptId, String ref, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(LEARNER + "/attempts/" + attemptId + "/answers/" + ref)
                .with(as("awa@test.local")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void publish(SimBlueprint bp, SimBlueprintSkill skill, String code, ItemType type, Map<String, Object> payload) {
        SimItem item = new SimItem();
        item.setCertification(bp.getCertification());
        item.setSkill(skill);
        item.setPublicCode(code);
        item.setCreatedBy("auteur@test.local");
        items.save(item);
        SimItemVersion v = new SimItemVersion();
        v.setItem(item);
        v.setVersionNo(1);
        v.setType(type);
        v.setDifficulty((short) 2);
        v.setStemEn("Question " + code);
        v.setPayload(registry.validateAndNormalize(type, payload));
        v.setExplanationFr(SECRET);
        v.setStatus(ItemStatus.PUBLISHED);
        v.setBlueprint(bp);
        v.setAuthor("auteur@test.local");
        v.setReviewer("relecteur@test.local");
        v.setPublisher("relecteur@test.local");
        v.setPublishedAt(LocalDateTime.now());
        versions.save(v);
    }
}
