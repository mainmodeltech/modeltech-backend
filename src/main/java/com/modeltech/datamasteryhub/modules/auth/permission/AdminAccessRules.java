package com.modeltech.datamasteryhub.modules.auth.permission;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.ArrayList;
import java.util.List;

import static com.modeltech.datamasteryhub.modules.auth.permission.Permission.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Qui peut appeler quoi dans le back-office : <b>une seule table</b> relie chaque route {@code /api/v1/admin/**} au(x)
 * droit(s) qu'elle exige. Les règles sont évaluées dans l'ordre (la première qui correspond s'applique) ; tout ce qui n'est
 * pas listé est <b>refusé</b> (refus par défaut). Un test vérifie que chaque route d'administration existante est couverte.
 *
 * <p>Ajouter une route d'administration sans la rattacher à un droit la rend inaccessible : c'est voulu.
 */
public final class AdminAccessRules {

    /** {@code method == null} : toutes les méthodes. Plusieurs autorités : il suffit d'en avoir une. */
    public record Rule(HttpMethod method, String pattern, List<String> anyOf) {}

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // ── Tableaux de bord, « Mon espace » ─────────────────────────────────
        authority(GET, "/api/v1/admin/stats/actions", Permission.BACKOFFICE_ACCESS);   // pastilles du menu (filtrées par droit côté service)
        rule(GET, "/api/v1/admin/stats/**", DASHBOARD_FINANCE, DASHBOARD_ACTIVITY);      // les montants sont masqués sans DASHBOARD_FINANCE
        authority(null, "/api/v1/admin/me/**", Permission.BACKOFFICE_ACCESS);

        // ── Administration ───────────────────────────────────────────────────
        rule(null, "/api/v1/admin/users/**", ACCOUNTS_MANAGE);
        rule(GET, "/api/v1/admin/profiles", PROFILES_MANAGE, ACCOUNTS_MANAGE);             // choisir les profils d'un compte
        rule(null, "/api/v1/admin/profiles/**", PROFILES_MANAGE);
        rule(null, "/api/v1/admin/audit/**", AUDIT_VIEW);
        rule(POST, "/api/v1/admin/email-logs/test", SYSTEM_MANAGE);
        rule(GET, "/api/v1/admin/email-logs/**", EMAIL_LOGS_VIEW);
        rule(GET, "/api/v1/admin/trainers", SESSIONS_ASSIGN, ACCOUNTS_MANAGE);            // choisir le formateur d'une session

        // ── Admissions ───────────────────────────────────────────────────────
        rule(GET, "/api/v1/admin/registrations/*/invoices", INVOICES_VIEW);
        rule(POST, "/api/v1/admin/registrations/*/invoice", INVOICES_MANAGE);
        rule(POST, "/api/v1/admin/registrations/*/payments", PAYMENTS_MANAGE);            // paiement reçu hors site
        rule(POST, "/api/v1/admin/registrations", ENROLLMENTS_MANAGE);                    // inscription manuelle
        rule(GET, "/api/v1/admin/registrations/**", APPLICATIONS_VIEW);
        rule(GET, "/api/v1/admin/enrollments/**", APPLICATIONS_VIEW);
        rule(null, "/api/v1/admin/registrations/**", APPLICATIONS_DECIDE);                // accepter, refuser, annuler, changer le statut, supprimer

        // ── Argent ───────────────────────────────────────────────────────────
        rule(GET, "/api/v1/admin/payments/**", PAYMENTS_VIEW);
        rule(POST, "/api/v1/admin/payments/*/refund", PAYMENTS_REFUND);
        rule(null, "/api/v1/admin/payments/**", PAYMENTS_MANAGE);                         // confirmer, refuser, relancer
        rule(GET, "/api/v1/admin/invoices/**", INVOICES_VIEW);
        rule(null, "/api/v1/admin/invoices/**", INVOICES_MANAGE);
        rule(GET, "/api/v1/admin/promo-codes/**", PROMO_VIEW);
        rule(null, "/api/v1/admin/promo-codes/**", PROMO_MANAGE);
        rule(GET, "/api/v1/admin/partners/**", PARTNERS_VIEW);
        rule(null, "/api/v1/admin/partners/**", PARTNERS_MANAGE, PARTNERS_REVENUE);       // la part de revenu exige PARTNERS_REVENUE (vérifié par le service)

        // ── Apprenants ───────────────────────────────────────────────────────
        rule(GET, "/api/v1/admin/learners/**", LEARNERS_VIEW);
        rule(null, "/api/v1/admin/learners/**", LEARNERS_MANAGE);

        // ── Catalogue et programme ───────────────────────────────────────────
        rule(null, "/api/v1/admin/bootcamps/sessions/*/trainer", SESSIONS_ASSIGN);
        // Modifier une formation / une session : le catalogue, ou les seuls tarifs (le service ne retient alors que les prix)
        rule(HttpMethod.PUT, "/api/v1/admin/bootcamps/sessions/*", CATALOG_MANAGE, PRICING_MANAGE);
        rule(HttpMethod.PUT, "/api/v1/admin/bootcamps/*", CATALOG_MANAGE, PRICING_MANAGE);
        rule(GET, "/api/v1/admin/bootcamps/**", CATALOG_VIEW);
        rule(null, "/api/v1/admin/bootcamps/**", CATALOG_MANAGE);                         // les prix exigent PRICING_MANAGE (vérifié par le service)
        rule(GET, "/api/v1/admin/domains/**", CATALOG_VIEW);
        rule(null, "/api/v1/admin/domains/**", CATALOG_MANAGE);
        rule(null, "/api/v1/admin/formations/**", PROGRAM_MANAGE);                        // contenu, projet final (une formation à la fois : voir CourseAccessPolicy)
        rule(null, "/api/v1/admin/lessons/**", PROGRAM_MANAGE);                           // banque de questions

        // ── Sessions, certificats ────────────────────────────────────────────
        rule(POST, "/api/v1/admin/sessions/*/learners/*/certificate", CERTIFICATES_MANAGE);
        rule(GET, "/api/v1/admin/sessions/**", SESSIONS_VIEW);
        rule(null, "/api/v1/admin/sessions/**", SESSIONS_TRACK);
        rule(GET, "/api/v1/admin/questions", SESSIONS_VIEW);                              // boîte de réception (portée : voir MessagingService.inbox)
        rule(GET, "/api/v1/admin/certificates/**", CERTIFICATES_VIEW);
        rule(null, "/api/v1/admin/certificates/**", CERTIFICATES_MANAGE);

        // ── Relations et contenu ─────────────────────────────────────────────
        rule(GET, "/api/v1/admin/masterclass/**", MASTERCLASS_VIEW);
        rule(null, "/api/v1/admin/contact-messages/**", MESSAGES_MANAGE);
        rule(null, "/api/v1/admin/newsletter-subscriptions/**", NEWSLETTER_MANAGE);
        rule(null, "/api/v1/admin/community/**", COMMUNITY_MODERATE);
        for (String content : List.of("site-settings", "services", "testimonials", "alumni", "projects")) {
            rule(GET, "/api/v1/admin/" + content + "/**", SITE_CONTENT_VIEW);
            rule(GET, "/api/v1/admin/" + content, SITE_CONTENT_VIEW);
            rule(null, "/api/v1/admin/" + content + "/**", SITE_CONTENT_MANAGE);
            rule(null, "/api/v1/admin/" + content, SITE_CONTENT_MANAGE);
        }
    }

    private AdminAccessRules() {}

    private static void rule(HttpMethod method, String pattern, Permission... anyOf) {
        RULES.add(new Rule(method, pattern, java.util.Arrays.stream(anyOf).map(Enum::name).toList()));
    }

    private static void authority(HttpMethod method, String pattern, String authority) {
        RULES.add(new Rule(method, pattern, List.of(authority)));
    }

    public static List<Rule> rules() {
        return List.copyOf(RULES);
    }

    /** Applique la table à la configuration Spring Security, puis ferme tout le reste de {@code /api/v1/admin/**}. */
    public static void apply(AuthorizeHttpRequestsConfigurer<org.springframework.security.config.annotation.web.builders.HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        for (Rule rule : RULES) {
            var matcher = rule.method() == null ? auth.requestMatchers(rule.pattern()) : auth.requestMatchers(rule.method(), rule.pattern());
            matcher.hasAnyAuthority(rule.anyOf().toArray(String[]::new));
        }
        auth.requestMatchers("/api/v1/admin/**").denyAll();
    }
}
