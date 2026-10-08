package com.modeltech.datamasteryhub.modules.stats;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestData;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentMethod;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.PaymentRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tableau de bord d'administration : compteurs « à traiter », indicateurs de période, droits d'accès. */
class StatsIT extends AbstractIntegrationTest {

    @Autowired private BootcampRepository bootcampRepository;
    @Autowired private BootcampSessionRepository sessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager entityManager;

    private final RequestPostProcessor admin = user("staff@test.local", "ADMIN");

    @BeforeEach
    void setUp() {
        Bootcamp bootcamp = bootcampRepository.save(TestData.bootcamp("power-bi", "Power BI", null));
        BootcampSession session = TestData.session(bootcamp, "Cohorte 1", LocalDate.now().plusDays(10), SessionStatus.OPEN);
        session.setMaxParticipants(10);
        session.setCurrentParticipants(4);
        session = sessionRepository.save(session);

        registration("a@test.local", bootcamp, session, RegistrationStatus.PENDING);
        registration("b@test.local", bootcamp, session, RegistrationStatus.PENDING);
        registration("c@test.local", bootcamp, session, RegistrationStatus.PAYMENT_PENDING);
        Registration paid = registration("d@test.local", bootcamp, session, RegistrationStatus.CONFIRMED);
        registration("e@test.local", bootcamp, session, RegistrationStatus.REJECTED);

        payment(paid, 1, 100_000L, PaymentStatus.CONFIRMED, PaymentMethod.WAVE, LocalDate.now().minusDays(1));
        payment(registrationOf("c@test.local"), 1, 50_000L, PaymentStatus.PENDING, null, LocalDate.now().minusDays(3));
        payment(registrationOf("c@test.local"), 2, 20_000L, PaymentStatus.DECLARED, PaymentMethod.ORANGE_MONEY, LocalDate.now().plusDays(3));
        entityManager.flush();
    }

    @Test
    void actions_count_what_waits_for_the_team() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/actions").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newApplications").value(2))
                .andExpect(jsonPath("$.data.paymentsToConfirm").value(1))
                .andExpect(jsonPath("$.data.overdueInstallments").value(1))
                .andExpect(jsonPath("$.data.unreadMessages").value(0))
                .andExpect(jsonPath("$.data.failedEmails").value(0));
    }

    @Test
    void overview_aggregates_funnel_revenue_and_session_fill() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("XOF"))
                .andExpect(jsonPath("$.data.registrations.total").value(5))
                .andExpect(jsonPath("$.data.registrations.byStatus.PENDING").value(2))
                .andExpect(jsonPath("$.data.registrations.byStatus.COMPLETED").value(0))
                .andExpect(jsonPath("$.data.funnel.submitted").value(5))
                .andExpect(jsonPath("$.data.funnel.accepted").value(2))
                .andExpect(jsonPath("$.data.funnel.paid").value(1))
                .andExpect(jsonPath("$.data.funnel.conversionRate").value(20.0))
                .andExpect(jsonPath("$.data.revenue.collected").value(100000))
                .andExpect(jsonPath("$.data.revenue.net").value(100000))
                .andExpect(jsonPath("$.data.revenue.outstanding").value(70000))
                .andExpect(jsonPath("$.data.revenue.overdue").value(50000))
                .andExpect(jsonPath("$.data.revenue.collectedByMethod.WAVE").value(100000))
                .andExpect(jsonPath("$.data.sessions[0].capacity").value(10))
                .andExpect(jsonPath("$.data.sessions[0].confirmed").value(4))
                .andExpect(jsonPath("$.data.sessions[0].fillRate").value(40))
                .andExpect(jsonPath("$.data.topFormations[0].title").value("Power BI"))
                .andExpect(jsonPath("$.data.topFormations[0].registrations").value(5))
                .andExpect(jsonPath("$.data.topFormations[0].collected").value(100000))
                .andExpect(jsonPath("$.data.breakdowns.bySource.WEBSITE").value(5))
                .andExpect(jsonPath("$.data.breakdowns.byCountry").isNotEmpty())
                .andExpect(jsonPath("$.data.monthly").isNotEmpty())
                .andExpect(jsonPath("$.data.learners.total").isNumber())
                .andExpect(jsonPath("$.data.audience.newsletterSubscribers").isNumber());
    }

    @Test
    void overview_outside_the_period_finds_nothing_new() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview").param("from", "2020-01-01").param("to", "2020-03-31").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrations.total").value(0))
                .andExpect(jsonPath("$.data.funnel.conversionRate").doesNotExist())
                .andExpect(jsonPath("$.data.revenue.collected").value(0))
                .andExpect(jsonPath("$.data.monthly.length()").value(3));
    }

    @Test
    void overview_rejects_inconsistent_or_oversized_periods() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview").param("from", "2026-05-10").param("to", "2026-05-01").with(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/stats/overview").param("from", "2024-01-01").param("to", "2026-01-01").with(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stats_are_reserved_to_administrators() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview")).andExpect(status().isForbidden());
        for (String role : new String[]{"EDITOR", "TRAINER", "PARTNER", "LEARNER"}) {
            mockMvc.perform(get("/api/v1/admin/stats/overview").with(user("x@test.local", role))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/stats/actions").with(user("x@test.local", role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/stats/actions").with(user("root@test.local", "SUPER_ADMIN"))).andExpect(status().isOk());
    }

    // ── Données ──────────────────────────────────────────────────────

    private Registration registration(String email, Bootcamp bootcamp, BootcampSession session, RegistrationStatus status) {
        Registration r = TestData.registration(email, bootcamp, session, null);
        r.setStatus(status);
        return registrationRepository.save(r);
    }

    private Registration registrationOf(String email) {
        return registrationRepository.findAll().stream().filter(r -> email.equals(r.getEmail())).findFirst().orElseThrow();
    }

    private void payment(Registration registration, int number, long amount, PaymentStatus status, PaymentMethod method, LocalDate due) {
        Payment p = new Payment();
        p.setRegistration(registration);
        p.setAmount(amount);
        p.setInstallmentNumber(number);
        p.setInstallmentCount(2);
        p.setStatus(status);
        p.setMethod(method);
        p.setDueDate(due);
        p.setPublicToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        p.setTokenExpiresAt(LocalDateTime.now().plusDays(30));
        if (status == PaymentStatus.CONFIRMED) p.setConfirmedAt(LocalDateTime.now());
        paymentRepository.save(p);
    }

    private static RequestPostProcessor user(String email, String role) {
        return SecurityMockMvcRequestPostProcessors.user(email).roles(role);
    }
}
