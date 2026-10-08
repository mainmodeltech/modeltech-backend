package com.modeltech.datamasteryhub.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.course.service.MessagingService;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rappels de live, messages de l'équipe à une session, questions des apprenants. */
class MessagingIT extends AbstractIntegrationTest {

    @MockBean private MessageDispatcher dispatcher;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private MessagingService messagingService;
    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LearnerRepository learnerRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;

    private Bootcamp bootcamp;
    private BootcampSession session;
    private Learner awa;
    private Learner moussa;
    private String liveId;
    private String videoId;
    private String draftLiveId;
    private final LocalDateTime now = LocalDateTime.now().withNano(0);
    private final RequestPostProcessor trainer = user("formateur@test.local", "TRAINER");

    @BeforeEach
    void setUp() throws Exception {
        reset(dispatcher);
        when(dispatcher.send(any(OutboundMessage.class))).thenReturn(true);

        bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().minusDays(7), SessionStatus.IN_PROGRESS);
        session.setEndDate(LocalDate.now().plusDays(60));
        session = sessionRepository.save(session);
        session.setTrainer(account("formateur@test.local", "Moussa Fall", "ROLE_TRAINER"));
        session = sessionRepository.save(session);

        JsonNode programme = json(mockMvc.perform(put("/api/v1/admin/formations/" + bootcamp.getId() + "/content").with(user("a@test.local", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"modules":[{"title":"Module 1","lessons":[
                                  {"title":"1.1 Intro","type":"VIDEO","status":"PUBLISHED"},
                                  {"title":"1.2 Atelier DAX","type":"LIVE","status":"PUBLISHED","liveAt":"%s","liveUrl":"https://meet.example.com/dax"},
                                  {"title":"1.3 Live caché","type":"LIVE","status":"DRAFT","liveAt":"%s"}]}]}"""
                                .formatted(iso(now.plusHours(20)), iso(now.plusHours(20)))))
                .andExpect(status().isOk()));
        videoId = programme.at("/modules/0/lessons/0/id").asText();
        liveId = programme.at("/modules/0/lessons/1/id").asText();
        draftLiveId = programme.at("/modules/0/lessons/2/id").asText();

        awa = enroll("awa@example.com", "Awa", LocalDate.now().minusDays(1));
        moussa = enroll("moussa@example.com", "Moussa", LocalDate.now().minusDays(1));
    }

    // ── Rappels de live ──────────────────────────────────────────────

    @Test
    void liveReminder_goesOnce24HoursBefore_thenOnceOneHourBefore() {
        // 20 h avant : rappel « demain »
        assertThat(messagingService.sendDueLiveReminders(now)).isEqualTo(2);
        List<OutboundMessage> first = sent(2);
        assertThat(first).extracting(OutboundMessage::toEmail).containsExactlyInAnyOrder("awa@example.com", "moussa@example.com");
        assertThat(first.get(0).type()).isEqualTo("LIVE_REMINDER");
        assertThat(first.get(0).subject()).startsWith("Rappel : live « 1.2 Atelier DAX »").endsWith("à " + hhmm(now.plusHours(20)));
        assertThat(first.get(0).body()).contains("Bonjour").contains("https://meet.example.com/dax").contains("Cohorte 1");

        // le planificateur repasse : rien de plus
        reset(dispatcher);
        when(dispatcher.send(any(OutboundMessage.class))).thenReturn(true);
        assertThat(messagingService.sendDueLiveReminders(now.plusMinutes(15))).isZero();
        assertThat(messagingService.sendDueLiveReminders(now.plusHours(5))).isZero();

        // 50 minutes avant : rappel « dans 1 heure », une seule fois
        LocalDateTime almost = now.plusHours(20).minusMinutes(50);
        assertThat(messagingService.sendDueLiveReminders(almost)).isEqualTo(2);
        assertThat(sent(2)).allSatisfy(m -> assertThat(m.subject()).isEqualTo("Dans 1 heure : live « 1.2 Atelier DAX »"));
        reset(dispatcher);
        when(dispatcher.send(any(OutboundMessage.class))).thenReturn(true);
        assertThat(messagingService.sendDueLiveReminders(almost.plusMinutes(15))).isZero();
        verify(dispatcher, never()).send(any(OutboundMessage.class));
    }

    @Test
    void liveReminder_skipsFarAwayLives_drafts_pastLives_andLearnersWhoseAccessHasNotOpened() {
        assertThat(messagingService.sendDueLiveReminders(now.minusHours(10))).isZero();   // live dans 30 h : trop tôt
        assertThat(messagingService.sendDueLiveReminders(now.plusHours(21))).isZero();    // live déjà commencé
        verify(dispatcher, never()).send(any(OutboundMessage.class));

        enroll("futur@example.com", "Futur", LocalDate.now().plusDays(10));
        assertThat(messagingService.sendDueLiveReminders(now)).isEqualTo(2);              // Awa et Moussa, pas l'apprenant « futur »
    }

    // ── Messages à une session ───────────────────────────────────────

    @Test
    void sessionMessage_reachesEveryLearner_inTheBackground_withTheSenderAsReplyAddress() throws Exception {
        String body = "{\"subject\":\"Support du live\",\"body\":\"Le support est en ligne.\"}";

        mockMvc.perform(post(sessionUrl() + "/messages").with(trainer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.recipientCount").value(2))
                .andExpect(jsonPath("$.data.sentBy").value("formateur@test.local"));

        ArgumentCaptor<List<OutboundMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).sendAll(sent.capture(), any());
        assertThat(sent.getValue()).hasSize(2);
        OutboundMessage message = sent.getValue().stream().filter(m -> m.toEmail().equals("awa@example.com")).findFirst().orElseThrow();
        assertThat(message.type()).isEqualTo("SESSION_MESSAGE");
        assertThat(message.subject()).isEqualTo("Support du live");
        assertThat(message.replyTo()).isEqualTo("formateur@test.local");
        assertThat(message.body()).startsWith("Bonjour Awa,").contains("Le support est en ligne.").contains("Moussa Fall").contains("Power BI");

        mockMvc.perform(get(sessionUrl() + "/messages").with(trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subject").value("Support du live"));
    }

    @Test
    void sessionMessage_canTargetSomeLearners_andIsValidatedAndScoped() throws Exception {
        mockMvc.perform(post(sessionUrl() + "/messages").with(trainer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Pour Awa\",\"body\":\"Bonjour\",\"learnerIds\":[\"" + awa.getId() + "\"]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.recipientCount").value(1));
        mockMvc.perform(post(sessionUrl() + "/messages").with(trainer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"x\",\"body\":\"y\",\"learnerIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(sessionUrl() + "/messages").with(trainer).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"\",\"body\":\"\"}"))
                .andExpect(status().isBadRequest());
        // un autre formateur, l'éditeur et l'anonyme n'écrivent pas aux apprenants de cette session
        account("autre@test.local", "Autre", "ROLE_TRAINER");
        String ok = "{\"subject\":\"s\",\"body\":\"b\"}";
        mockMvc.perform(post(sessionUrl() + "/messages").with(user("autre@test.local", "TRAINER")).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(sessionUrl() + "/messages").with(user("e@test.local", "EDITOR")).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(sessionUrl() + "/messages").with(user("a@test.local", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isAccepted());
    }

    // ── Questions ────────────────────────────────────────────────────

    @Test
    void question_isSentToTheTrainer_answeredByTheTeam_andReturnedToTheLearner() throws Exception {
        RequestPostProcessor awaAuth = user("awa@example.com", "LEARNER");

        JsonNode asked = json(mockMvc.perform(post("/api/v1/learner/lessons/" + videoId + "/questions").with(awaAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Comment filtrer sur deux colonnes ?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answer").doesNotExist()));
        ArgumentCaptor<List<OutboundMessage>> toTrainer = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).sendAll(toTrainer.capture(), isNull());
        assertThat(toTrainer.getValue().get(0).toEmail()).isEqualTo("formateur@test.local");
        assertThat(toTrainer.getValue().get(0).body()).contains("Awa Diop").contains("Comment filtrer sur deux colonnes ?").contains("/admin/sessions/" + session.getId());

        mockMvc.perform(get(sessionUrl() + "/questions").param("open", "true").with(trainer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].learnerName").value("Awa Diop"))
                .andExpect(jsonPath("$.data[0].lessonTitle").value("1.1 Intro"));

        String answerUrl = sessionUrl() + "/questions/" + asked.get("id").asText() + "/answer";
        mockMvc.perform(post(answerUrl).with(trainer).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        reset(dispatcher);
        mockMvc.perform(post(answerUrl).with(trainer).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"Utilisez FILTER avec AND.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredBy").value("Moussa Fall"));
        ArgumentCaptor<List<OutboundMessage>> toLearner = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).sendAll(toLearner.capture(), isNull());
        assertThat(toLearner.getValue().get(0).toEmail()).isEqualTo("awa@example.com");
        assertThat(toLearner.getValue().get(0).body()).contains("Utilisez FILTER avec AND.");
        mockMvc.perform(post(answerUrl).with(trainer).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"Encore\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/learner/lessons/" + videoId + "/questions").with(awaAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answer").value("Utilisez FILTER avec AND."))
                .andExpect(jsonPath("$[0].answeredBy").value("Moussa Fall"));
        mockMvc.perform(get("/api/v1/learner/lessons/" + videoId + "/questions").with(user("moussa@example.com", "LEARNER")))
                .andExpect(jsonPath("$.length()").value(0));   // les questions des autres restent privées
        mockMvc.perform(get(sessionUrl() + "/questions").with(user("autre@test.local", "TRAINER"))).andExpect(status().isForbidden());
    }

    @Test
    void questions_areLimited_andOnlyAboutLessonsTheLearnerCanOpen() throws Exception {
        RequestPostProcessor awaAuth = user("awa@example.com", "LEARNER");
        String url = "/api/v1/learner/lessons/" + videoId + "/questions";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(url).with(awaAuth).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Question numéro " + i + " ?\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post(url).with(awaAuth).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Une de trop ?\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(url).with(awaAuth).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"?\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/learner/lessons/" + draftLiveId + "/questions").with(awaAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Leçon brouillon ?\"}")).andExpect(status().isNotFound());

        learnerRepository.save(learner("intrus@example.com", "Intrus"));
        mockMvc.perform(post(url).with(user("intrus@example.com", "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Sans inscription ?\"}")).andExpect(status().isForbidden());
        verify(dispatcher, atLeastOnce()).sendAll(anyList(), isNull());
        verify(dispatcher, times(5)).sendAll(anyList(), isNull());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private List<OutboundMessage> sent(int expected) {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher, times(expected)).send(captor.capture());
        return captor.getAllValues();
    }

    private String sessionUrl() {
        return "/api/v1/admin/sessions/" + session.getId();
    }

    private Learner enroll(String email, String firstName, LocalDate accessStarts) {
        Learner learner = learnerRepository.save(learner(email, firstName));
        Registration reg = registrationRepository.save(TestData.registration(email, bootcamp, session, null));
        Enrollment e = new Enrollment();
        e.setLearner(learner);
        e.setRegistration(reg);
        e.setSession(session);
        e.setAccessStartsAt(accessStarts);
        e.setAccessEndsAt(session.getEndDate());
        enrollmentRepository.save(e);
        return learner;
    }

    private Learner learner(String email, String firstName) {
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName(firstName);
        l.setLastName("Diop");
        return l;
    }

    private AdminUser account(String email, String name, String role) {
        AdminUser a = new AdminUser();
        a.setEmail(email);
        a.setFullName(name);
        a.setPasswordHash("x");
        a.setRoles(new HashSet<>(List.of(roleRepository.findByName(role).orElseThrow())));
        return adminUserRepository.save(a);
    }

    private String iso(LocalDateTime t) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(t);
    }

    private String hhmm(LocalDateTime t) {
        return "%02dh%02d".formatted(t.getHour(), t.getMinute());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
