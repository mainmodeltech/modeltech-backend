package com.modeltech.datamasteryhub.modules.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Masterclass : événements gérés par l'équipe, inscriptions du public (places, clôture), présences, message aux inscrits. */
class MasterclassEventsIT extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/masterclasses";

    @Autowired private ObjectMapper objectMapper;
    @MockBean private RecaptchaService recaptcha;
    @MockBean private MessageDispatcher dispatcher;

    private final RequestPostProcessor comm = TestAuth.as("com@test.local", "COMMUNICATION");

    @BeforeEach
    void setUp() {
        reset(dispatcher);
        when(recaptcha.verify(anyString(), anyString())).thenReturn(true);
        when(dispatcher.send(any(OutboundMessage.class))).thenReturn(true);
    }

    @Test
    void teamCreatesAnEvent_publicSeesItWithoutTheJoinLink_andRegistrationsRespectCapacity() throws Exception {
        JsonNode created = json(mockMvc.perform(post(ADMIN).with(comm).contentType(MediaType.APPLICATION_JSON).content(event("Power BI pour débutants", 2, true)))
                .andExpect(status().isCreated())).get("data");
        String code = created.get("code").asText();
        assertThat(code).startsWith("power-bi-pour-debutants-").matches(".*\\d{4}-\\d{2}$");
        assertThat(created.get("status").asText()).isEqualTo("OPEN");

        // Le public voit l'événement, ses places, mais jamais le lien de connexion
        String publicBody = mockMvc.perform(get("/api/v1/masterclass/upcoming")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value(code)).andExpect(jsonPath("$.data[0].spotsLeft").value(2))
                .andExpect(jsonPath("$.data[0].registrationOpen").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(publicBody).doesNotContain("meet.example.com");

        register(code, "awa@example.com").andExpect(status().isCreated());
        // La confirmation part par la messagerie de la plateforme, avec le lien de connexion
        ArgumentCaptor<OutboundMessage> sent = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo("MASTERCLASS_CONFIRMATION");
        assertThat(sent.getValue().toEmail()).isEqualTo("awa@example.com");
        assertThat(sent.getValue().body()).contains("meet.example.com");

        register(code, "awa@example.com").andExpect(status().isConflict());            // doublon
        register(code, "moussa@example.com").andExpect(status().isCreated());
        register(code, "troisieme@example.com").andExpect(status().isConflict());      // complet
        mockMvc.perform(get("/api/v1/masterclass/" + code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.full").value(true)).andExpect(jsonPath("$.data.registrationOpen").value(false));
        register("inconnue", "x@example.com").andExpect(status().isConflict());        // n'existe pas
    }

    @Test
    void anUnpublishedClosedOrPastEvent_acceptsNoRegistration_andIsNotPublic() throws Exception {
        String draft = createCode(event("Brouillon", null, false));
        register(draft, "a@example.com").andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/masterclass/" + draft)).andExpect(status().isNotFound());

        JsonNode closed = json(mockMvc.perform(post(ADMIN).with(comm).contentType(MediaType.APPLICATION_JSON)
                .content(event("Fermée", null, true).replace("\"registrationsClosed\":false", "\"registrationsClosed\":true"))).andExpect(status().isCreated())).get("data");
        register(closed.get("code").asText(), "b@example.com").andExpect(status().isConflict());

        String past = objectMapper.writeValueAsString(java.util.Map.of("title", "Passée", "startsAt", LocalDateTime.now().minusDays(3).withNano(0).toString(),
                "durationMinutes", 120, "format", "ONLINE", "published", true));
        register(createCode(past), "c@example.com").andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/masterclass/upcoming")).andExpect(jsonPath("$.data.length()").value(1));   // seule « Fermée » est à venir ET publiée
    }

    @Test
    void attendanceAndMessage_targetTheRightRegistrants() throws Exception {
        JsonNode created = json(mockMvc.perform(post(ADMIN).with(comm).contentType(MediaType.APPLICATION_JSON).content(event("Atelier", null, true)))
                .andExpect(status().isCreated())).get("data");
        String id = created.get("id").asText();
        String code = created.get("code").asText();
        register(code, "awa@example.com").andExpect(status().isCreated());
        register(code, "moussa@example.com").andExpect(status().isCreated());
        reset(dispatcher);

        JsonNode regs = json(mockMvc.perform(get(ADMIN + "/" + id + "/registrations").with(comm)).andExpect(status().isOk()));
        assertThat(regs.get("data").size()).isEqualTo(2);
        String awaId = null;
        for (JsonNode r : regs.get("data")) if ("awa@example.com".equals(r.get("email").asText())) awaId = r.get("id").asText();

        mockMvc.perform(patch(ADMIN + "/" + id + "/registrations/" + awaId + "/attendance").with(comm).contentType(MediaType.APPLICATION_JSON)
                .content("{\"attendance\":\"ATTENDED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.attendance").value("ATTENDED"));
        mockMvc.perform(patch(ADMIN + "/" + id + "/registrations/" + awaId + "/attendance").with(comm).contentType(MediaType.APPLICATION_JSON)
                .content("{\"attendance\":\"PRESENT\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(get(ADMIN + "/" + id).with(comm)).andExpect(jsonPath("$.data.registered").value(2)).andExpect(jsonPath("$.data.attended").value(1));

        mockMvc.perform(post(ADMIN + "/" + id + "/message").with(comm).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Merci d'être venu\",\"body\":\"Voici le support.\",\"audience\":\"ATTENDED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recipients").value(1));
        ArgumentCaptor<List<OutboundMessage>> batch = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).sendAll(batch.capture(), isNull());
        assertThat(batch.getValue()).extracting(OutboundMessage::toEmail).containsExactly("awa@example.com");
    }

    @Test
    void management_needsTheMasterclassRight_andAnEventWithRegistrantsIsNotDeleted() throws Exception {
        mockMvc.perform(post(ADMIN).with(TestAuth.as("adm@test.local", "ADMISSIONS")).contentType(MediaType.APPLICATION_JSON).content(event("X", null, true)))
                .andExpect(status().isForbidden());                                                  // Admissions voit, ne gère pas
        mockMvc.perform(get(ADMIN).with(TestAuth.as("adm@test.local", "ADMISSIONS"))).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN).with(TestAuth.as("f@test.local", "FINANCE"))).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());

        JsonNode created = json(mockMvc.perform(post(ADMIN).with(comm).contentType(MediaType.APPLICATION_JSON).content(event("Supprimable", 5, true)))
                .andExpect(status().isCreated())).get("data");
        String id = created.get("id").asText();
        register(created.get("code").asText(), "awa@example.com").andExpect(status().isCreated());
        mockMvc.perform(delete(ADMIN + "/" + id).with(comm)).andExpect(status().isConflict());
        // La capacité ne peut pas passer sous le nombre d'inscrits
        mockMvc.perform(put(ADMIN + "/" + id).with(comm).contentType(MediaType.APPLICATION_JSON).content(event("Supprimable", 5, true).replace("\"capacity\":5", "\"capacity\":1")))
                .andExpect(status().isOk());
        register(created.get("code").asText(), "moussa@example.com").andExpect(status().isConflict());   // 1 place, déjà prise

        // Sans inscrit, la suppression est possible
        JsonNode empty = json(mockMvc.perform(post(ADMIN).with(comm).contentType(MediaType.APPLICATION_JSON).content(event("Vide", null, false)))
                .andExpect(status().isCreated())).get("data");
        mockMvc.perform(delete(ADMIN + "/" + empty.get("id").asText()).with(comm)).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN + "/" + empty.get("id").asText()).with(comm)).andExpect(status().isNotFound());
    }

    // ── utilitaires ──────────────────────────────────────────────────

    private String event(String title, Integer capacity, boolean published) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "Une heure pour comprendre.");
        body.put("startsAt", LocalDateTime.now().plusDays(10).withNano(0).toString());
        body.put("durationMinutes", 120);
        body.put("format", "ONLINE");
        body.put("place", "En ligne (Google Meet)");
        body.put("joinUrl", "https://meet.example.com/abc-defg");
        if (capacity != null) body.put("capacity", capacity);
        body.put("published", published);
        body.put("registrationsClosed", false);
        return objectMapper.writeValueAsString(body);
    }

    private String createCode(String body) throws Exception {
        return json(mockMvc.perform(post(ADMIN).with(comm).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()))
                .get("data").get("code").asText();
    }

    private ResultActions register(String code, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/masterclass/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"masterclassId\":\"" + code + "\",\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"" + email
                        + "\",\"phone\":\"771234567\",\"recaptchaToken\":\"ok\"}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
