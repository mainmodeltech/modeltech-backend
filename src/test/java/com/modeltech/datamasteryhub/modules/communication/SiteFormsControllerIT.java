package com.modeltech.datamasteryhub.modules.communication;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.communication.enums.ContactType;
import com.modeltech.datamasteryhub.modules.communication.repository.ContactMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Formulaires publics du site : POST /contact-messages, /diagnostic-requests,
 * /partner-applications — et leur consultation admin (/admin/contact-messages).
 */
class SiteFormsControllerIT extends AbstractIntegrationTest {

    @Autowired private ContactMessageRepository contactMessageRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ── POST /contact-messages ───────────────────────────────────────

    @Test
    void contact_isPublic_storesATypedMessage_andNotifiesTheTeam() throws Exception {
        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa","lastName":"Ndiaye","email":"awa@exemple.sn","phone":"+221770000000",
                                 "subject":"Candidater à une cohorte","requesterType":"PARTICULIER",
                                 "message":"Bonjour, je souhaite candidater."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("CONTACT"))
                .andExpect(jsonPath("$.data.requesterType").value("PARTICULIER"))
                .andExpect(jsonPath("$.data.status").value("unread"))
                .andExpect(jsonPath("$.data.details").doesNotExist());

        verify(notificationService).notifyNewContactMessage(any(ContactMessage.class));
    }

    @Test
    void contact_acceptsAOneWordName_theLastNameIsStoredEmpty() throws Exception {
        // Le front n'envoie que « Nom et prénom » : un seul mot donnait un 400 (lastName obligatoire)
        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Fatou","lastName":"","email":"fatou@exemple.sn","message":"Bonjour"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firstName").value("Fatou"))
                .andExpect(jsonPath("$.data.lastName").value(""));

        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Moussa\",\"email\":\"moussa@exemple.sn\",\"message\":\"Bonjour\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lastName").value(""));
    }

    @Test
    void contact_requesterTypeIsOptional_andLegacyPayloadStillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"A","lastName":"B","email":"a@b.sn","company":null,"subject":null,
                                 "phone":null,"message":"m"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requesterType").doesNotExist())
                .andExpect(jsonPath("$.data.type").value("CONTACT"));
    }

    @Test
    void contact_validatesTheBody_andNeverNotifiesOnError() throws Exception {
        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"email\":\"pas-un-email\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.firstName").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.message").exists());

        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"email\":\"a@b.sn\",\"message\":\"m\",\"requesterType\":\"ROBOT\"}"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).notifyNewContactMessage(any());
    }

    // ── POST /diagnostic-requests ────────────────────────────────────

    @Test
    void diagnostic_storesAStructuredMessage_visibleInTheAdminMessages() throws Exception {
        mockMvc.perform(post("/api/v1/diagnostic-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa","lastName":"Ndiaye","email":"awa@banque.sn","phone":"+221771112233",
                                 "company":"Banque X","role":"DAF","peopleCount":"RANGE_6_15","need":"POWER_BI_CUSTOM",
                                 "context":"Reporting manuel chaque fin de mois"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("DIAGNOSTIC"))
                .andExpect(jsonPath("$.data.requesterType").value("ENTREPRISE"))
                .andExpect(jsonPath("$.data.subject").value("Diagnostic entreprise"))
                .andExpect(jsonPath("$.data.company").value("Banque X"))
                .andExpect(jsonPath("$.data.details.role").value("DAF"))
                .andExpect(jsonPath("$.data.details.peopleCount").value("RANGE_6_15"))
                .andExpect(jsonPath("$.data.details.need").value("POWER_BI_CUSTOM"))
                .andExpect(jsonPath("$.data.message").value(
                        "Fonction : DAF\nNombre de personnes à former : 6 à 15\nBesoin principal : Power BI sur mesure\n"
                                + "Contexte : Reporting manuel chaque fin de mois"));

        verify(notificationService).notifyNewContactMessage(any(ContactMessage.class));
        assertThat(contactMessageRepository.findAll()).hasSize(1)
                .allSatisfy(m -> assertThat(m.getType()).isEqualTo(ContactType.DIAGNOSTIC));
    }

    @Test
    void diagnostic_contextAndPhoneAreOptional_theOneWordNameIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/diagnostic-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Awa","email":"awa@banque.sn","company":"Banque X","role":"DRH",
                                 "peopleCount":"OVER_50","need":"UNDECIDED"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lastName").value(""))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.endsWith("Contexte : —")));
    }

    @Test
    void diagnostic_validatesRequiredFieldsAndEnums() throws Exception {
        mockMvc.perform(post("/api/v1/diagnostic-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.company").exists())
                .andExpect(jsonPath("$.validationErrors.role").exists())
                .andExpect(jsonPath("$.validationErrors.peopleCount").exists())
                .andExpect(jsonPath("$.validationErrors.need").exists());

        mockMvc.perform(post("/api/v1/diagnostic-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"A","email":"a@b.sn","company":"C","role":"R",
                                 "peopleCount":"BEAUCOUP","need":"UNDECIDED"}"""))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).notifyNewContactMessage(any());
    }

    // ── POST /partner-applications ───────────────────────────────────

    @Test
    void partnerApplication_storesAStructuredMessage() throws Exception {
        mockMvc.perform(post("/api/v1/partner-applications").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Cheikh","lastName":"Fall","email":"cheikh@agile.sn","phone":"+221775556677",
                                 "organization":"Agile Sénégal","domain":"PROJECT_AGILE",
                                 "linkedinUrl":"https://linkedin.com/in/cheikh","proposal":"Scrum Master PSM I, 3 jours",
                                 "references":"PSM I, 200 participants"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("PARTNER_APPLICATION"))
                .andExpect(jsonPath("$.data.subject").value("Candidature partenaire formateur"))
                .andExpect(jsonPath("$.data.company").value("Agile Sénégal"))
                .andExpect(jsonPath("$.data.requesterType").doesNotExist())
                .andExpect(jsonPath("$.data.details.domain").value("PROJECT_AGILE"))
                .andExpect(jsonPath("$.data.details.proposal").value("Scrum Master PSM I, 3 jours"))
                .andExpect(jsonPath("$.data.message").value(
                        "Organisme : Agile Sénégal\nDomaine : Gestion de projet & Agile\n"
                                + "Profil LinkedIn : https://linkedin.com/in/cheikh\n"
                                + "Formation proposée : Scrum Master PSM I, 3 jours\nRéférences : PSM I, 200 participants"));

        verify(notificationService).notifyNewContactMessage(any(ContactMessage.class));
    }

    @Test
    void partnerApplication_validatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/partner-applications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.domain").exists())
                .andExpect(jsonPath("$.validationErrors.proposal").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists());

        mockMvc.perform(post("/api/v1/partner-applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"email\":\"a@b.sn\",\"domain\":\"MAGIE\",\"proposal\":\"p\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /admin/contact-messages ──────────────────────────────────

    @Test
    void admin_listsAllMessagesAndFiltersByType() throws Exception {
        mockMvc.perform(post("/api/v1/contact-messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"email\":\"a@b.sn\",\"message\":\"contact\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/diagnostic-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"B\",\"email\":\"b@b.sn\",\"company\":\"C\",\"role\":\"R\","
                        + "\"peopleCount\":\"RANGE_1_5\",\"need\":\"EXCEL_UPGRADE\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/partner-applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"C\",\"email\":\"c@b.sn\",\"domain\":\"OTHER\",\"proposal\":\"p\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/contact-messages").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(3));

        mockMvc.perform(get("/api/v1/admin/contact-messages").param("type", "DIAGNOSTIC").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].type").value("DIAGNOSTIC"))
                .andExpect(jsonPath("$.data[0].details.need").value("EXCEL_UPGRADE"));

        mockMvc.perform(get("/api/v1/admin/contact-messages").param("type", "PARTNER_APPLICATION").with(admin()))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        mockMvc.perform(get("/api/v1/admin/contact-messages").param("type", "INCONNU").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_legacyMessagesWithoutTypeStillListWithTheSameShape() throws Exception {
        // Ligne insérée comme le faisait l'ancien code : sans type/requester_type/details
        jdbcTemplate.update("INSERT INTO contact_messages (id, first_name, last_name, email, message, status) "
                + "VALUES (gen_random_uuid(), 'Historique', 'Message', 'old@exemple.sn', 'Ancien message', 'unread')");

        mockMvc.perform(get("/api/v1/admin/contact-messages").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].firstName").value("Historique"))
                .andExpect(jsonPath("$.data[0].phone").value((Object) null))
                .andExpect(jsonPath("$.data[0].type").value("CONTACT"))
                .andExpect(jsonPath("$.data[0].requesterType").doesNotExist())
                .andExpect(jsonPath("$.data[0].details").doesNotExist());
    }

    @Test
    void admin_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/contact-messages")).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.local").roles("ADMIN");
    }
}
