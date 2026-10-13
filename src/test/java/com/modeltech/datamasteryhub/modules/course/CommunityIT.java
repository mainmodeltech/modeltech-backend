package com.modeltech.datamasteryhub.modules.course;

import com.modeltech.datamasteryhub.TestAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Communauté : fil d'entraide par formation, réservé aux apprenants inscrits, modéré par l'équipe. */
class CommunityIT extends AbstractIntegrationTest {

    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private Bootcamp bootcamp;
    private final RequestPostProcessor asAwa = TestAuth.as("awa@example.com", "LEARNER");
    private final RequestPostProcessor asMoussa = TestAuth.as("moussa@example.com", "LEARNER");
    private final RequestPostProcessor asOutsider = TestAuth.as("autre@example.com", "LEARNER");

    @BeforeEach
    void setUp() {
        bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        BootcampSession session = sessionRepository.save(
                TestData.session(bootcamp, "Cohorte 1", LocalDate.now().minusDays(7), SessionStatus.IN_PROGRESS));
        enroll("awa@example.com", "Awa", "Diop", session);
        enroll("moussa@example.com", "Moussa", "Sow", session);
        learnerRepository.save(learner("autre@example.com", "Autre", "Personne"));
    }

    @Test
    void learnersOfAFormation_postAndReply_underAPublicNameWithoutEmail() throws Exception {
        String rootId = id(send("/api/v1/learner/formations/" + bootcamp.getId() + "/community", asAwa,
                "{\"content\":\"Quelqu'un a un exemple de mesure DAX ?\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Awa D.")).andExpect(jsonPath("$.mine").value(true)));
        send("/api/v1/learner/formations/" + bootcamp.getId() + "/community", asMoussa,
                "{\"content\":\"Oui : CALCULATE(SUM(...), filtre)\",\"parentId\":\"" + rootId + "\"}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/learner/formations/" + bootcamp.getId() + "/community").with(asMoussa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalThreads").value(1))
                .andExpect(jsonPath("$.threads[0].author").value("Awa D."))
                .andExpect(jsonPath("$.threads[0].mine").value(false))
                .andExpect(jsonPath("$.threads[0].replies[0].author").value("Moussa S."))
                .andExpect(jsonPath("$.threads[0].replies[0].mine").value(true))
                .andExpect(jsonPath("$.threads[0].email").doesNotExist());
    }

    @Test
    void validation_andAccess() throws Exception {
        String url = "/api/v1/learner/formations/" + bootcamp.getId() + "/community";
        send(url, asAwa, "{\"content\":\"   \"}").andExpect(status().isBadRequest());
        send(url, asAwa, "{\"content\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
        // répondre à un message inexistant
        send(url, asAwa, "{\"content\":\"salut\",\"parentId\":\"" + java.util.UUID.randomUUID() + "\"}").andExpect(status().isBadRequest());

        // sans accès à la formation : ni lecture ni écriture
        mockMvc.perform(get(url).with(asOutsider)).andExpect(status().isForbidden());
        send(url, asOutsider, "{\"content\":\"hello\"}").andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void anAuthorDeletesOnlyTheirOwnMessage_andTheRepliesGoWithIt() throws Exception {
        String url = "/api/v1/learner/formations/" + bootcamp.getId() + "/community";
        String rootId = id(send(url, asAwa, "{\"content\":\"Question\"}").andExpect(status().isCreated()));
        send(url, asMoussa, "{\"content\":\"Réponse\",\"parentId\":\"" + rootId + "\"}").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/learner/community/" + rootId).with(asMoussa)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/learner/community/" + rootId).with(asAwa)).andExpect(status().isNoContent());
        mockMvc.perform(get(url).with(asAwa)).andExpect(jsonPath("$.totalThreads").value(0));
        mockMvc.perform(delete("/api/v1/learner/community/" + rootId).with(asAwa)).andExpect(status().isNotFound());
    }

    @Test
    void theTeamModerates_withTheRealIdentityOfTheAuthors() throws Exception {
        String url = "/api/v1/learner/formations/" + bootcamp.getId() + "/community";
        String rootId = id(send(url, asAwa, "{\"content\":\"Message à modérer\"}").andExpect(status().isCreated()));

        RequestPostProcessor admin = TestAuth.as("staff@test.local", "ADMIN");
        mockMvc.perform(get("/api/v1/admin/community").param("formationId", bootcamp.getId().toString()).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].authorName").value("Awa Diop"))
                .andExpect(jsonPath("$.data[0].authorEmail").value("awa@example.com"));

        mockMvc.perform(delete("/api/v1/admin/community/" + rootId).with(TestAuth.as("editor@test.local", "EDITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/community/" + rootId).with(asAwa)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/community/" + rootId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/admin/community/" + rootId).with(admin)).andExpect(status().isNoContent());
        mockMvc.perform(get(url).with(asAwa)).andExpect(jsonPath("$.totalThreads").value(0));
    }

    // ── Données ──────────────────────────────────────────────────────

    private ResultActions send(String url, RequestPostProcessor as, String body) throws Exception {
        return mockMvc.perform(post(url).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions result) throws Exception {
        JsonNode node = objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return node.get("id").asText();
    }

    private void enroll(String email, String firstName, String lastName, BootcampSession session) {
        Learner learner = learnerRepository.save(learner(email, firstName, lastName));
        Registration reg = TestData.registration(email, bootcamp, session, null);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setLearner(learner);
        reg = registrationRepository.save(reg);
        Enrollment enrollment = new Enrollment();
        enrollment.setLearner(learner);
        enrollment.setRegistration(reg);
        enrollment.setSession(session);
        enrollment.setAccessStartsAt(LocalDate.now().minusDays(7));
        enrollment.setAccessEndsAt(LocalDate.now().plusDays(60));
        enrollmentRepository.save(enrollment);
    }

    private static Learner learner(String email, String firstName, String lastName) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName(firstName);
        l.setLastName(lastName);
        return l;
    }
}
