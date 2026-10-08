package com.modeltech.datamasteryhub.modules.training;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Formateur d'une session : affectation (ADMIN), liste des formateurs, accès limité à ses propres sessions. */
class TrainerAssignmentIT extends AbstractIntegrationTest {

    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private RoleRepository roleRepository;

    private BootcampSession mine;
    private BootcampSession other;
    private AdminUser trainer;

    @BeforeEach
    void setUp() {
        Bootcamp bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        mine = sessionRepository.save(TestData.session(bootcamp, "Cohorte 1", LocalDate.now(), SessionStatus.IN_PROGRESS));
        other = sessionRepository.save(TestData.session(bootcamp, "Cohorte 2", LocalDate.now(), SessionStatus.IN_PROGRESS));
        trainer = account("formateur@test.local", "Moussa Fall", "ROLE_TRAINER");
        account("editeur@test.local", "Éditrice", "ROLE_EDITOR");
    }

    @Test
    void trainersList_containsOnlyActiveTrainers() throws Exception {
        AdminUser inactive = account("ancien@test.local", "Ancien Formateur", "ROLE_TRAINER");
        inactive.setActive(false);
        adminUserRepository.save(inactive);

        mockMvc.perform(get("/api/v1/admin/trainers").with(user("e@test.local", "EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Moussa Fall"));
    }

    @Test
    void assignment_isForAdmins_andOnlyToActiveTrainers() throws Exception {
        String url = "/api/v1/admin/bootcamps/sessions/" + mine.getId() + "/trainer";
        String body = "{\"trainerId\":\"" + trainer.getId() + "\"}";

        mockMvc.perform(put(url).with(user("e@test.local", "EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(url).with(user("a@test.local", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerId\":\"" + adminUserRepository.findByEmailAndIsDeletedFalse("editeur@test.local").orElseThrow().getId() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(url).with(user("a@test.local", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(url).with(user("a@test.local", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerName").value("Moussa Fall"))
                .andExpect(jsonPath("$.trainerId").value(trainer.getId().toString()));

        mockMvc.perform(put(url).with(user("a@test.local", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerName").doesNotExist());
    }

    @Test
    void aTrainerOnlySeesTheSessionsEntrustedToHim_theAdministrationSeesAll() throws Exception {
        mine.setTrainer(trainer);
        sessionRepository.save(mine);
        RequestPostProcessor moussa = user("formateur@test.local", "TRAINER");

        mockMvc.perform(get("/api/v1/admin/sessions/" + mine.getId() + "/tracking").with(moussa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerName").value("Moussa Fall"));
        mockMvc.perform(get("/api/v1/admin/sessions/" + other.getId() + "/tracking").with(moussa))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/sessions/" + other.getId() + "/tracking").with(user("a@test.local", "ADMIN")))
                .andExpect(status().isOk());
        // un autre formateur n'a pas accès à la session de Moussa
        account("autre@test.local", "Autre Formateur", "ROLE_TRAINER");
        mockMvc.perform(get("/api/v1/admin/sessions/" + mine.getId() + "/tracking").with(user("autre@test.local", "TRAINER")))
                .andExpect(status().isForbidden());
    }

    private AdminUser account(String email, String name, String role) {
        AdminUser a = new AdminUser();
        a.setEmail(email);
        a.setFullName(name);
        a.setPasswordHash("x");
        a.setRoles(new HashSet<>(List.of(roleRepository.findByName(role).orElseThrow())));
        return adminUserRepository.save(a);
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
