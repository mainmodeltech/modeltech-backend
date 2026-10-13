package com.modeltech.datamasteryhub.modules.auth;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.TestAuth;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.permission.AdminAccessRules;
import com.modeltech.datamasteryhub.modules.auth.permission.DefaultProfiles;
import com.modeltech.datamasteryhub.modules.auth.permission.Permission;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrôle d'accès du back-office par droit : (1) chaque route d'administration existante est rattachée à un droit —
 * en ajouter une sans règle la rend inaccessible, ce test l'oblige à la déclarer ; (2) la matrice « profil livré ×
 * action » correspond à la répartition décidée avec la direction ; (3) la migration V33 et {@link DefaultProfiles}
 * n'ont pas divergé.
 */
class AdminAccessRulesIT extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/v1/admin";
    private static final String ID = UUID.randomUUID().toString();

    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @Autowired RoleRepository roleRepository;

    // ── (1) Couverture ────────────────────────────────────────────────

    @Test
    void everyAdminRouteIsCoveredByARule() {
        AntPathMatcher matcher = new AntPathMatcher();
        List<String> uncovered = new ArrayList<>();
        int checked = 0;
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() == null ? Set.of()
                    : info.getPathPatternsCondition().getPatterns().stream().map(p -> p.getPatternString()).collect(Collectors.toSet());
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith(ADMIN)) continue;
                String url = pattern.replaceAll("\\{[^/}]+}", "x");
                for (RequestMethod m : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    checked++;
                    boolean covered = AdminAccessRules.rules().stream().anyMatch(r ->
                            (r.method() == null || r.method().name().equals(m.name())) && matcher.match(r.pattern(), url));
                    if (!covered) uncovered.add(m + " " + pattern);
                }
            }
        }
        assertThat(checked).as("des routes d'administration sont bien détectées").isGreaterThan(80);
        assertThat(uncovered).as("routes d'administration sans règle d'accès").isEmpty();
    }

    @Test
    void everyRuleNamesAKnownPermission() {
        Set<String> known = new TreeSet<>(Permission.names());
        known.add(Permission.BACKOFFICE_ACCESS);
        for (AdminAccessRules.Rule rule : AdminAccessRules.rules()) {
            assertThat(known).as(rule.pattern()).containsAll(rule.anyOf());
        }
    }

    // ── (2) Matrice profil × action ───────────────────────────────────

    @Test
    void financeHandlesMoneyButNotAdmissionsDecisionsNorAccounts() throws Exception {
        allowed("FINANCE", HttpMethod.GET, "/payments");
        allowed("FINANCE", HttpMethod.POST, "/payments/" + ID + "/refund");
        allowed("FINANCE", HttpMethod.POST, "/payments/" + ID + "/confirm");
        allowed("FINANCE", HttpMethod.GET, "/invoices");
        allowed("FINANCE", HttpMethod.GET, "/promo-codes");
        allowed("FINANCE", HttpMethod.GET, "/stats/overview");
        denied("FINANCE", HttpMethod.POST, "/registrations/" + ID + "/accept");
        allowed("FINANCE", HttpMethod.POST, "/registrations/" + ID + "/payments");   // paiement reçu hors site
        denied("FINANCE", HttpMethod.GET, "/users");
        denied("FINANCE", HttpMethod.GET, "/profiles");
        denied("FINANCE", HttpMethod.GET, "/audit");
        denied("FINANCE", HttpMethod.GET, "/community/posts");
        denied("FINANCE", HttpMethod.POST, "/formations/" + ID + "/content");
    }

    @Test
    void admissionsDecideApplicationsButCannotTouchMoney() throws Exception {
        allowed("ADMISSIONS", HttpMethod.GET, "/registrations");
        allowed("ADMISSIONS", HttpMethod.POST, "/registrations/" + ID + "/accept");
        allowed("ADMISSIONS", HttpMethod.GET, "/payments");           // voit le statut
        allowed("ADMISSIONS", HttpMethod.GET, "/learners");
        allowed("ADMISSIONS", HttpMethod.GET, "/stats/overview");     // tableau d'activité (montants masqués par le service)
        denied("ADMISSIONS", HttpMethod.POST, "/payments/" + ID + "/confirm");
        denied("ADMISSIONS", HttpMethod.POST, "/payments/" + ID + "/refund");
        denied("ADMISSIONS", HttpMethod.GET, "/invoices");
        denied("ADMISSIONS", HttpMethod.GET, "/promo-codes");
        denied("ADMISSIONS", HttpMethod.GET, "/partners");
        denied("ADMISSIONS", HttpMethod.POST, "/bootcamps");
        denied("ADMISSIONS", HttpMethod.GET, "/users");
    }

    @Test
    void pedagogyRunsProgramAndCertificatesButSeesNoMoney() throws Exception {
        allowed("PEDAGOGY", HttpMethod.GET, "/bootcamps");
        allowed("PEDAGOGY", HttpMethod.POST, "/bootcamps");
        allowed("PEDAGOGY", HttpMethod.GET, "/sessions/" + ID + "/tracking");
        allowed("PEDAGOGY", HttpMethod.POST, "/sessions/" + ID + "/learners/" + ID + "/certificate");
        allowed("PEDAGOGY", HttpMethod.PUT, "/formations/" + ID + "/content");
        denied("PEDAGOGY", HttpMethod.GET, "/payments");
        denied("PEDAGOGY", HttpMethod.GET, "/invoices");
        denied("PEDAGOGY", HttpMethod.GET, "/registrations");
        denied("PEDAGOGY", HttpMethod.GET, "/users");
    }

    @Test
    void communicationRunsSiteAndRelationsButNothingElse() throws Exception {
        allowed("COMMUNICATION", HttpMethod.GET, "/newsletter-subscriptions");
        allowed("COMMUNICATION", HttpMethod.GET, "/contact-messages");
        allowed("COMMUNICATION", HttpMethod.GET, "/site-settings");
        allowed("COMMUNICATION", HttpMethod.GET, "/testimonials");
        denied("COMMUNICATION", HttpMethod.GET, "/registrations");
        denied("COMMUNICATION", HttpMethod.GET, "/payments");
        denied("COMMUNICATION", HttpMethod.GET, "/learners");
        denied("COMMUNICATION", HttpMethod.GET, "/bootcamps");
        denied("COMMUNICATION", HttpMethod.GET, "/stats/overview");
    }

    @Test
    void trainerOnlyFollowsSessions() throws Exception {
        allowed("TRAINER", HttpMethod.GET, "/me/sessions");
        allowed("TRAINER", HttpMethod.GET, "/sessions/" + ID + "/tracking");
        denied("TRAINER", HttpMethod.GET, "/payments");
        denied("TRAINER", HttpMethod.GET, "/registrations");
        denied("TRAINER", HttpMethod.GET, "/learners");
        denied("TRAINER", HttpMethod.GET, "/bootcamps");
        denied("TRAINER", HttpMethod.GET, "/stats/overview");
        denied("TRAINER", HttpMethod.POST, "/sessions/" + ID + "/learners/" + ID + "/certificate");
    }

    @Test
    void auditorReadsButNeverWrites() throws Exception {
        allowed("AUDITOR", HttpMethod.GET, "/payments");
        allowed("AUDITOR", HttpMethod.GET, "/invoices");
        allowed("AUDITOR", HttpMethod.GET, "/audit");
        allowed("AUDITOR", HttpMethod.GET, "/stats/overview");
        denied("AUDITOR", HttpMethod.POST, "/payments/" + ID + "/confirm");
        denied("AUDITOR", HttpMethod.POST, "/payments/" + ID + "/refund");
        denied("AUDITOR", HttpMethod.POST, "/registrations/" + ID + "/accept");
        denied("AUDITOR", HttpMethod.POST, "/bootcamps");
        denied("AUDITOR", HttpMethod.GET, "/users");
    }

    @Test
    void directionOpensEverythingAndUnknownRoutesStayClosed() throws Exception {
        allowed("SUPER_ADMIN", HttpMethod.GET, "/users");
        allowed("SUPER_ADMIN", HttpMethod.GET, "/profiles");
        allowed("SUPER_ADMIN", HttpMethod.GET, "/audit");
        allowed("SUPER_ADMIN", HttpMethod.POST, "/payments/" + ID + "/refund");
        // Refus par défaut : une route d'administration non déclarée est fermée, même à la direction
        denied("SUPER_ADMIN", HttpMethod.GET, "/route-non-declaree");
        denied("ADMIN", HttpMethod.GET, "/route-non-declaree");
    }

    @Test
    void legacyAdminKeepsEverythingExceptAccountsProfilesSystemAndAudit() throws Exception {
        allowed("ADMIN", HttpMethod.POST, "/payments/" + ID + "/refund");
        allowed("ADMIN", HttpMethod.GET, "/registrations");
        denied("ADMIN", HttpMethod.GET, "/users");
        denied("ADMIN", HttpMethod.GET, "/profiles");
        denied("ADMIN", HttpMethod.GET, "/audit");
        denied("ADMIN", HttpMethod.POST, "/email-logs/test");
    }

    @Test
    void learnerAndAnonymousNeverReachTheBackOffice() throws Exception {
        for (String path : List.of("/payments", "/registrations", "/users", "/me/sessions", "/stats/actions")) {
            mockMvc.perform(req(HttpMethod.GET, path).with(TestAuth.as("awa@example.com", "LEARNER"))).andExpect(status().isForbidden());
            mockMvc.perform(req(HttpMethod.GET, path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void customProfileGetsExactlyItsPermissions() throws Exception {
        var auth = TestAuth.withPermissions("custom@test.local", Permission.PAYMENTS_VIEW, Permission.INVOICES_VIEW);
        mockMvc.perform(req(HttpMethod.GET, "/payments").with(auth)).andExpect(status().is(org.hamcrest.Matchers.not(403)));
        mockMvc.perform(req(HttpMethod.GET, "/invoices").with(auth)).andExpect(status().is(org.hamcrest.Matchers.not(403)));
        mockMvc.perform(req(HttpMethod.POST, "/payments/" + ID + "/confirm").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(req(HttpMethod.GET, "/registrations").with(auth)).andExpect(status().isForbidden());
    }

    // ── (3) Dérive migration / définition ─────────────────────────────

    @Test
    void migrationSeedMatchesDefaultProfiles() {
        for (var profile : DefaultProfiles.all().values()) {
            Role role = roleRepository.findByName(profile.name()).orElseThrow(() -> new AssertionError("profil absent : " + profile.name()));
            assertThat(role.isStaff()).as(profile.name()).isTrue();
            assertThat(role.isSystem()).as(profile.name()).isTrue();
            Set<String> expected = RoleNames.SUPER_ADMIN.equals(profile.name()) ? Set.of()
                    : profile.permissions().stream().map(Enum::name).collect(Collectors.toSet());
            assertThat(role.getPermissions()).as("droits de " + profile.name()).containsExactlyInAnyOrderElementsOf(expected);
        }
        assertThat(roleRepository.findByName(RoleNames.LEARNER).orElseThrow().isStaff()).isFalse();
    }

    // ── utilitaires ───────────────────────────────────────────────────

    private void allowed(String profile, HttpMethod method, String path) throws Exception {
        int status = mockMvc.perform(req(method, path).with(TestAuth.as("p@test.local", profile)))
                .andReturn().getResponse().getStatus();
        assertThat(status).as(profile + " " + method + " " + path + " devrait être autorisé").isNotEqualTo(403).isNotEqualTo(401);
    }

    private void denied(String profile, HttpMethod method, String path) throws Exception {
        mockMvc.perform(req(method, path).with(TestAuth.as("p@test.local", profile))).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder req(HttpMethod method, String path) {
        return MockMvcRequestBuilders.request(method, ADMIN + path).contentType("application/json").content("{}");
    }
}
