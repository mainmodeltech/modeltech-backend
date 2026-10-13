package com.modeltech.datamasteryhub.modules.training;

import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** « Mon espace » du back-office : un formateur ne voit que ses sessions, un partenaire que ses formations. */
class AdminMeIT extends AbstractIntegrationTest {

    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        Partner acme = partnerRepository.save(TestData.partner("acme-me", "Acme"));
        Partner other = partnerRepository.save(TestData.partner("autre-me", "Autre"));

        Bootcamp internal = bootcampRepository.save(TestData.bootcamp("me-interne", "Formation interne", null));
        Bootcamp partnered = TestData.bootcamp("me-acme", "Formation Acme", null);
        partnered.setDeliveredBy(DeliveredBy.PARTNER);
        partnered.setPartner(acme);
        partnered = bootcampRepository.save(partnered);
        Bootcamp foreign = TestData.bootcamp("me-autre", "Formation Autre", null);
        foreign.setDeliveredBy(DeliveredBy.PARTNER);
        foreign.setPartner(other);
        bootcampRepository.save(foreign);

        AdminUser trainer = account("formateur-me@test.local", "ROLE_TRAINER", null);
        BootcampSession mine = TestData.session(internal, "Ma session", LocalDate.now().plusDays(5), SessionStatus.OPEN);
        mine.setTrainer(trainer);
        sessionRepository.save(mine);
        sessionRepository.save(TestData.session(partnered, "Session sans formateur", LocalDate.now().plusDays(9), SessionStatus.OPEN));

        account("partenaire-me@test.local", "ROLE_PARTNER", acme);
        account("sans-lien-me@test.local", "ROLE_PARTNER", null);
    }

    @Test
    void trainer_seesOnlyTheSessionsEntrustedToThem_andTheirFormations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me/sessions").with(user("formateur-me@test.local", "TRAINER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sessionName").value("Ma session"))
                .andExpect(jsonPath("$.data[0].formationTitle").value("Formation interne"));

        mockMvc.perform(get("/api/v1/admin/me/formations").with(user("formateur-me@test.local", "TRAINER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Formation interne"));
    }

    @Test
    void partner_seesOnlyItsOwnFormations_andNoSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me/formations").with(user("partenaire-me@test.local", "PARTNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Formation Acme"))
                .andExpect(jsonPath("$.data[0].partnerName").value("Acme"));

        mockMvc.perform(get("/api/v1/admin/me/sessions").with(user("partenaire-me@test.local", "PARTNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void partnerWithoutPartnerLink_seesNothing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me/formations").with(user("sans-lien-me@test.local", "PARTNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void administration_seesEverything() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me/sessions").with(user("admin@test.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/admin/me/formations").with(user("admin@test.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void anonymousAndLearners_areRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/me/formations").with(user("awa@example.com", "LEARNER")))
                .andExpect(status().isForbidden());
    }

    private AdminUser account(String email, String role, Partner partner) {
        AdminUser a = new AdminUser();
        a.setEmail(email);
        a.setFullName("Compte " + email);
        a.setPasswordHash("x");
        a.setPartner(partner);
        a.setRoles(new HashSet<>(List.of(roleRepository.findByName(role).orElseThrow())));
        return adminUserRepository.save(a);
    }

    private static RequestPostProcessor user(String email, String role) {
        return TestAuth.as(email, role);
    }
}
