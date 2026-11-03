package com.modeltech.datamasteryhub.modules.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.audit.repository.AuditRepository;
import com.modeltech.datamasteryhub.modules.auth.service.LearnerService;
import com.modeltech.datamasteryhub.modules.communication.service.RecaptchaService;
import com.modeltech.datamasteryhub.modules.legal.repository.LegalAcceptanceRepository;
import com.modeltech.datamasteryhub.modules.legal.repository.LegalDocumentRepository;
import com.modeltech.datamasteryhub.modules.legal.service.LegalService;
import com.modeltech.datamasteryhub.modules.legal.service.LegalTemplates;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.notification.service.TemplatedMailer;
import com.modeltech.datamasteryhub.modules.payments.GatewayHealth;
import com.modeltech.datamasteryhub.modules.payments.gateway.PaymentGateway;
import com.modeltech.datamasteryhub.modules.coaching.repository.CoachingRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Textes légaux : publication réservée à la Direction (et bloquée tant que le texte contient des champs à compléter ou n'est pas validé par le
 * conseil), exigence d'acceptation contrôlée côté serveur à chaque point d'engagement, preuves d'acceptation versionnées.
 */
class LegalIT extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/v1/admin/legal";
    private static final String PUBLIC = "/api/v1/legal";
    private static final String CLEAN_BODY = "## Article 1\n\nTexte complet, sans champ restant à compléter.";

    @MockBean private TemplatedMailer mailer;
    @MockBean private StorageService storage;
    @MockBean private PaymentGateway gateway;
    @MockBean private RecaptchaService recaptcha;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private LegalDocumentRepository documents;
    @Autowired private LegalAcceptanceRepository acceptances;
    @Autowired private CoachingRequestRepository coachingRequests;
    @Autowired private LegalTemplates templates;
    @Autowired private LegalService legal;
    @Autowired private LearnerService learnerService;
    @Autowired private AuditRepository audit;
    @Autowired private GatewayHealth health;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private static RequestPostProcessor direction() {
        return TestAuth.as("dir@test.local", "SUPER_ADMIN");
    }

    @BeforeEach
    void setUp() {
        health.reset();
        when(mailer.send(anyString(), anyString(), any(), anyString())).thenReturn(true);
        when(recaptcha.verify(anyString(), anyString())).thenReturn(true);
        when(gateway.configured()).thenReturn(true);
        when(gateway.provider()).thenReturn("PAYDUNYA");
        when(gateway.mode()).thenReturn("sandbox");
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions saveDraft(LegalCode code, String body) throws Exception {
        return mockMvc.perform(put(ADMIN + "/" + code + "/draft").with(direction()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("title", code.title(), "body", body, "changeSummary", "Mise à jour"))));
    }

    private ResultActions publish(LegalCode code, String counsel, String on) throws Exception {
        return mockMvc.perform(post(ADMIN + "/" + code + "/publish").with(direction()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"counselValidatedBy\":\"" + counsel + "\",\"counselValidatedOn\":\"" + on + "\",\"changeSummary\":\"Première publication\"}"));
    }

    /** Publie un texte complet (brouillon sans champ à compléter, validé par le conseil). */
    private UUID publishClean(LegalCode code) throws Exception {
        saveDraft(code, CLEAN_BODY + " (" + code + ")").andExpect(status().isOk());
        return UUID.fromString(json(publish(code, "Me Diallo, avocat", LocalDate.now().toString()).andExpect(status().isOk())).at("/data/id").asText());
    }

    private void sync() {
        em.flush();
        em.clear();
    }

    // ── Modèles et publication ───────────────────────────────────────

    @Test
    void theTemplatesLoadAsDraftsOnly_andCannotBePublishedWhileFieldsAreLeftToFill() throws Exception {
        assertThat(templates.seedMissing()).isEqualTo(4);
        assertThat(templates.seedMissing()).isZero();           // jamais deux fois, jamais d'écrasement
        JsonNode overview = json(mockMvc.perform(get(ADMIN).with(direction())).andExpect(status().isOk())).at("/data");
        assertThat(overview).hasSize(4);
        for (JsonNode o : overview) {
            assertThat(o.at("/published").isNull()).isTrue();                       // rien n'est publié de lui-même
            assertThat(o.at("/draft/hasPlaceholders").asBoolean()).isTrue();
        }
        publish(LegalCode.CGV, "Me Diallo", LocalDate.now().toString()).andExpect(status().isBadRequest());
        assertThat(json(mockMvc.perform(get(PUBLIC + "/documents"))).at("/data")).isEmpty();
        mockMvc.perform(get(PUBLIC + "/documents/CGV")).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheDirectionManagesTheLegalTexts() throws Exception {
        for (String profile : new String[]{"ADMIN", "PEDAGOGY", "FINANCE", "ADMISSIONS", "AUDITOR"}) {
            mockMvc.perform(get(ADMIN).with(TestAuth.as("x@test.local", profile))).andExpect(status().isForbidden());
            mockMvc.perform(put(ADMIN + "/CGU/draft").with(TestAuth.as("x@test.local", profile)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"t\",\"body\":\"b\"}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN + "/acceptances").with(TestAuth.as("x@test.local", "ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN).with(direction())).andExpect(status().isOk());
    }

    @Test
    void publishingNeedsTheCounselValidation_thenFreezesTheVersion_andNewVersionsReplaceIt() throws Exception {
        saveDraft(LegalCode.CGU, CLEAN_BODY).andExpect(status().isOk());
        publish(LegalCode.CGU, "", LocalDate.now().toString()).andExpect(status().isBadRequest());                      // qui a validé ?
        publish(LegalCode.CGU, "Me Diallo", LocalDate.now().plusDays(3).toString()).andExpect(status().isBadRequest());   // date dans le futur

        JsonNode v1 = json(publish(LegalCode.CGU, "Me Diallo", LocalDate.now().toString()).andExpect(status().isOk())).at("/data");
        assertThat(v1.at("/version").asInt()).isEqualTo(1);
        assertThat(v1.at("/status").asText()).isEqualTo("PUBLISHED");
        assertThat(v1.at("/counselValidatedBy").asText()).isEqualTo("Me Diallo");
        JsonNode pub = json(mockMvc.perform(get(PUBLIC + "/documents/CGU")).andExpect(status().isOk())).at("/data");
        assertThat(pub.at("/body").asText()).isEqualTo(CLEAN_BODY);
        assertThat(audit.findAll()).extracting(a -> a.getAction()).contains("LEGAL_PUBLISHED");
        publish(LegalCode.CGU, "Me Diallo", LocalDate.now().toString()).andExpect(status().isNotFound());                  // plus de brouillon

        // une modification ouvre la version 2 : la version en vigueur reste intacte jusqu'à la publication
        saveDraft(LegalCode.CGU, CLEAN_BODY + "\n\nArticle ajouté.").andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get(PUBLIC + "/documents/CGU"))).at("/data/body").asText()).isEqualTo(CLEAN_BODY);
        JsonNode v2 = json(publish(LegalCode.CGU, "Me Diallo", LocalDate.now().toString()).andExpect(status().isOk())).at("/data");
        assertThat(v2.at("/version").asInt()).isEqualTo(2);
        sync();
        assertThat(documents.findByCodeOrderByVersionDesc(LegalCode.CGU)).extracting(d -> d.getStatus().name()).containsExactly("PUBLISHED", "SUPERSEDED");
        assertThat(json(mockMvc.perform(get(PUBLIC + "/documents")).andExpect(status().isOk())).at("/data")).hasSize(1);
    }

    @Test
    void aDraftCanBeAbandoned_withoutTouchingThePublishedVersion() throws Exception {
        publishClean(LegalCode.CGV);
        saveDraft(LegalCode.CGV, "Autre texte").andExpect(status().isOk());
        mockMvc.perform(delete(ADMIN + "/CGV/draft").with(direction())).andExpect(status().isOk());
        sync();
        assertThat(documents.findByCodeOrderByVersionDesc(LegalCode.CGV)).hasSize(1);
        mockMvc.perform(get(PUBLIC + "/documents/CGV")).andExpect(status().isOk());
    }

    // ── Exigence d'acceptation ───────────────────────────────────────

    private String order(String email, String accepted) {
        return "{\"plan\":\"PRO\",\"channel\":\"MANUAL\",\"firstName\":\"Awa\",\"email\":\"" + email + "\",\"acceptTerms\":true" + (accepted == null ? "" : ",\"acceptedDocuments\":" + accepted) + "}";
    }

    private void manualOnly() {
        em.flush();
        jdbc.update("UPDATE payment_service_setting SET mode = 'MANUAL_ONLY' WHERE service = 'SIMULATOR'");
        em.clear();
    }

    @Test
    void whileNothingIsPublished_nothingBlocks() throws Exception {
        manualOnly();
        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON).content(order("awa@test.local", null))).andExpect(status().isCreated());
        assertThat(json(mockMvc.perform(get(PUBLIC + "/requirements?context=SIMULATOR_ORDER"))).at("/data/documents")).isEmpty();
        assertThat(acceptances.count()).isZero();
    }

    @Test
    void aPurchaseNeedsTheCurrentTexts_checkedByTheServer_andLeavesAVersionedProof() throws Exception {
        manualOnly();
        UUID cgu = publishClean(LegalCode.CGU);
        UUID cgv = publishClean(LegalCode.CGV);
        UUID privacy = publishClean(LegalCode.PRIVACY);

        JsonNode req = json(mockMvc.perform(get(PUBLIC + "/requirements?context=SIMULATOR_ORDER")).andExpect(status().isOk())).at("/data/documents");
        assertThat(req).hasSize(3);

        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON).content(order("awa@test.local", null))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON).content(order("awa@test.local", "[\"" + cgu + "\"]"))).andExpect(status().isConflict());   // incomplet
        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order("awa@test.local", "[\"" + UUID.randomUUID() + "\",\"" + cgv + "\",\"" + privacy + "\"]"))).andExpect(status().isConflict());   // mauvaise version
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sim_order", Integer.class)).isZero();                          // rien n'est créé sans acceptation

        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order("awa@test.local", "[\"" + cgu + "\",\"" + cgv + "\",\"" + privacy + "\"]"))).andExpect(status().isCreated());
        sync();
        var proofs = acceptances.findAll();
        assertThat(proofs).hasSize(3);
        assertThat(proofs).extracting(a -> a.getDocumentCode()).containsExactlyInAnyOrder("CGU", "CGV", "PRIVACY");
        assertThat(proofs).allSatisfy(a -> {
            assertThat(a.getEmail()).isEqualTo("awa@test.local");
            assertThat(a.getSubjectType()).isEqualTo("SIM_ORDER");
            assertThat(a.getContext()).isEqualTo("SIMULATOR_ORDER");
            assertThat(a.getContentHash()).hasSize(64);
            assertThat(a.getDocumentVersion()).isEqualTo(1);
            assertThat(a.getAcceptedAt()).isNotNull();
        });
        JsonNode list = json(mockMvc.perform(get(ADMIN + "/acceptances?email=awa@test").with(direction())).andExpect(status().isOk())).at("/data");
        assertThat(list).hasSize(3);
        assertThat(json(mockMvc.perform(get(ADMIN + "/acceptances?code=CGV").with(direction()))).at("/data")).hasSize(1);
    }

    @Test
    void whenATextIsUpdated_oldVersionsAreRefused_untilTheNewOneIsAccepted() throws Exception {
        manualOnly();
        UUID cguV1 = publishClean(LegalCode.CGU);
        UUID cgv = publishClean(LegalCode.CGV);
        UUID privacy = publishClean(LegalCode.PRIVACY);
        saveDraft(LegalCode.CGU, CLEAN_BODY + "\n\nNouvel article.").andExpect(status().isOk());
        UUID cguV2 = UUID.fromString(json(publish(LegalCode.CGU, "Me Diallo", LocalDate.now().toString()).andExpect(status().isOk())).at("/data/id").asText());

        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order("awa@test.local", "[\"" + cguV1 + "\",\"" + cgv + "\",\"" + privacy + "\"]"))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/simulator/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order("awa@test.local", "[\"" + cguV2 + "\",\"" + cgv + "\",\"" + privacy + "\"]"))).andExpect(status().isCreated());
        sync();
        assertThat(acceptances.findAll()).filteredOn(a -> a.getDocumentCode().equals("CGU")).extracting(a -> a.getDocumentVersion()).containsExactly(2);
    }

    @Test
    void aCandidacyNeedsTheCurrentTexts() throws Exception {
        UUID cgu = publishClean(LegalCode.CGU);
        UUID cgv = publishClean(LegalCode.CGV);
        UUID privacy = publishClean(LegalCode.PRIVACY);
        String base = "\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"awa@example.com\",\"country\":\"Sénégal\",\"profile\":\"ENTREPRENEUR\",\"recaptchaToken\":\"jeton\"";
        mockMvc.perform(post("/api/v1/registrations").contentType(MediaType.APPLICATION_JSON).content("{" + base + "}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + ",\"acceptedDocuments\":[\"" + cgu + "\",\"" + cgv + "\",\"" + privacy + "\"]}")).andExpect(status().is2xxSuccessful());
        sync();
        assertThat(acceptances.findAll()).hasSize(3).allSatisfy(a -> assertThat(a.getSubjectType()).isEqualTo("REGISTRATION"));
    }

    @Test
    void aCoachingRequestNeedsTheTexts_andThePaymentPageNeedsTheSalesConditionsBeforeAnythingCanBePaid() throws Exception {
        UUID cgu = publishClean(LegalCode.CGU);
        UUID privacy = publishClean(LegalCode.PRIVACY);
        String form = "{\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"email\":\"awa@test.local\",\"acceptTerms\":true%s}";
        mockMvc.perform(post("/api/v1/coaching-requests").contentType(MediaType.APPLICATION_JSON).content(form.formatted(""))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/coaching-requests").contentType(MediaType.APPLICATION_JSON)
                .content(form.formatted(",\"acceptedDocuments\":[\"" + cgu + "\",\"" + privacy + "\"]"))).andExpect(status().isCreated());
        sync();
        assertThat(acceptances.findAll()).extracting(a -> a.getSubjectType()).containsOnly("COACHING_REQUEST");

        // validation par l'équipe : le lien de paiement existe
        UUID id = coachingRequests.findAll().get(0).getId();
        mockMvc.perform(post("/api/v1/admin/coaching-requests/" + id + "/validate").with(TestAuth.as("pedago@test.local", "PEDAGOGY")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerLabel\":\"2 séances\",\"amountXof\":60000,\"trainerName\":\"Cédric\",\"trainerConfirmed\":true}")).andExpect(status().isOk());
        sync();
        String token = coachingRequests.findById(id).orElseThrow().getPayToken();

        UUID cgv = publishClean(LegalCode.CGV);          // les conditions de vente ne sont publiées qu'ensuite : elles s'imposent au paiement
        em.flush();
        jdbc.update("UPDATE payment_service_setting SET mode = 'ONLINE_AND_MANUAL' WHERE service = 'COACHING'");
        em.clear();
        JsonNode view = json(mockMvc.perform(get("/api/v1/coaching/payments/" + token)).andExpect(status().isOk())).at("/data");
        assertThat(view.at("/termsToAccept")).hasSize(1);
        mockMvc.perform(post("/api/v1/coaching/payments/" + token + "/online")).andExpect(status().isConflict());                  // pas de paiement sans acceptation
        mockMvc.perform(post("/api/v1/coaching/payments/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/coaching/payments/" + token + "/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documents\":[\"" + UUID.randomUUID() + "\"]}")).andExpect(status().isConflict());                    // mauvaise version
        mockMvc.perform(post("/api/v1/coaching/payments/" + token + "/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documents\":[\"" + cgv + "\"]}")).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get("/api/v1/coaching/payments/" + token))).at("/data/termsToAccept")).isEmpty();
        mockMvc.perform(post("/api/v1/coaching/payments/" + token + "/declaration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WAVE\",\"reference\":\"WV-1\"}")).andExpect(status().isOk());                              // maintenant possible
        sync();
        assertThat(acceptances.findAll()).filteredOn(a -> a.getSubjectType().equals("COACHING_PAYMENT")).extracting(a -> a.getDocumentCode()).containsExactly("CGV");
    }

    // ── Apprenants ───────────────────────────────────────────────────

    @Test
    void aLearnerMustAcceptTheUsageTermsOnceperVersion() throws Exception {
        learnerService.findOrCreateWithoutInvitation("Awa", "Diop", "awa@test.local", null);
        learnerService.findOrCreateWithoutInvitation("Moussa", "Fall", "moussa@test.local", null);
        RequestPostProcessor awa = TestAuth.as("awa@test.local", "LEARNER");
        RequestPostProcessor moussa = TestAuth.as("moussa@test.local", "LEARNER");
        assertThat(json(mockMvc.perform(get("/api/v1/learner/legal/pending").with(awa)).andExpect(status().isOk())).at("/data")).isEmpty();   // rien de publié

        UUID cgu = publishClean(LegalCode.CGU);
        UUID privacy = publishClean(LegalCode.PRIVACY);
        assertThat(json(mockMvc.perform(get("/api/v1/learner/legal/pending").with(awa))).at("/data")).hasSize(2);
        mockMvc.perform(get("/api/v1/learner/legal/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/learner/legal/accept").with(awa).contentType(MediaType.APPLICATION_JSON).content("{\"documents\":[\"" + cgu + "\"]}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/learner/legal/accept").with(awa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documents\":[\"" + cgu + "\",\"" + privacy + "\"]}")).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get("/api/v1/learner/legal/pending").with(awa))).at("/data")).isEmpty();
        assertThat(json(mockMvc.perform(get("/api/v1/learner/legal/pending").with(moussa))).at("/data")).hasSize(2);                          // un autre apprenant n'est pas concerné

        // nouvelle version des CGU : l'apprenant doit l'accepter à son tour, mais pas la politique inchangée
        saveDraft(LegalCode.CGU, CLEAN_BODY + "\n\nNouvel article.").andExpect(status().isOk());
        publish(LegalCode.CGU, "Me Diallo", LocalDate.now().toString()).andExpect(status().isOk());
        JsonNode pending = json(mockMvc.perform(get("/api/v1/learner/legal/pending").with(awa))).at("/data");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).at("/code").asText()).isEqualTo("CGU");
        assertThat(pending.get(0).at("/version").asInt()).isEqualTo(2);
    }

    @Test
    void theTextsExposeNothingButTheirContent() throws Exception {
        publishClean(LegalCode.MENTIONS);
        String body = mockMvc.perform(get(PUBLIC + "/documents")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("dir@test.local").doesNotContain("Me Diallo").doesNotContain("counsel");        // ni auteur ni avocat dans l'index public
        List<String> keys = new ArrayList<>();
        objectMapper.readTree(body).at("/data/0").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("id", "code", "title", "version", "publishedAt", "path");
    }
}
