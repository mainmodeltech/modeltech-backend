package com.modeltech.datamasteryhub.modules.stats.service.impl;

import com.modeltech.datamasteryhub.modules.stats.dto.StatsPayloads;
import com.modeltech.datamasteryhub.modules.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/**
 * Agrégats en SQL (une requête par indicateur) : le tableau de bord ne charge jamais les entités en mémoire.
 * Tout ce qui est supprimé (soft delete) est ignoré.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final List<String> REGISTRATION_STATUSES = List.of("PENDING", "PAYMENT_PENDING", "PAYMENT_TO_CONFIRM",
            "CONFIRMED", "COMPLETED", "CANCELLED", "REJECTED");
    private static final String ACCEPTED = "(status IN ('PAYMENT_PENDING','PAYMENT_TO_CONFIRM','CONFIRMED','COMPLETED') OR accepted_at IS NOT NULL)";
    private static final String PAID = "status IN ('CONFIRMED','COMPLETED')";

    private final NamedParameterJdbcTemplate jdbc;

    @Value("${app.timezone:Africa/Dakar}")
    private String timezone;

    // =========================================================================
    //  À TRAITER
    // =========================================================================

    @Override
    public StatsPayloads.Actions actions() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("now", now).addValue("in7days", now.plusDays(7)).addValue("ago7days", now.minusDays(7));
        return StatsPayloads.Actions.builder()
                .newApplications(count("SELECT count(*) FROM registrations WHERE is_deleted = false AND status = 'PENDING'", p))
                .paymentsToConfirm(count("SELECT count(*) FROM payments WHERE is_deleted = false AND status = 'DECLARED'", p))
                .overdueInstallments(count("""
                        SELECT count(*) FROM payments p JOIN registrations r ON r.id = p.registration_id
                        WHERE p.is_deleted = false AND p.status = 'PENDING' AND p.due_date < CURRENT_DATE
                          AND r.is_deleted = false AND r.status IN ('PAYMENT_PENDING','CONFIRMED')
                        """, p))
                .unreadMessages(count("SELECT count(*) FROM contact_messages WHERE is_deleted = false AND status = 'unread'", p))
                .openQuestions(count("SELECT count(*) FROM lesson_questions WHERE is_deleted = false AND answer IS NULL AND session_id IS NOT NULL", p))
                .projectsToReview(count("SELECT count(*) FROM project_submissions WHERE is_deleted = false AND status = 'SUBMITTED'", p))
                .upcomingLives(count("""
                        SELECT count(*) FROM course_lessons
                        WHERE is_deleted = false AND type = 'LIVE' AND status <> 'DRAFT' AND live_at BETWEEN :now AND :in7days
                        """, p))
                .failedEmails(count("SELECT count(*) FROM email_logs WHERE status = 'FAILED' AND created_at >= :ago7days", p))
                .build();
    }

    // =========================================================================
    //  INDICATEURS
    // =========================================================================

    @Override
    public StatsPayloads.Overview overview(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneId.of(timezone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit précéder la date de fin.");
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La période ne peut pas dépasser " + MAX_DAYS + " jours.");
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", start.atStartOfDay())
                .addValue("to", end.plusDays(1).atStartOfDay());

        return StatsPayloads.Overview.builder()
                .from(start).to(end).currency("XOF")
                .registrations(registrations(p))
                .funnel(funnel(p))
                .revenue(revenue(p))
                .monthly(monthly(start, end, p))
                .sessions(sessions())
                .topFormations(topFormations(p))
                .breakdowns(breakdowns(p))
                .learners(learners(p))
                .audience(audience(p))
                .build();
    }

    private StatsPayloads.Registrations registrations(MapSqlParameterSource p) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        REGISTRATION_STATUSES.forEach(s -> byStatus.put(s, 0L));
        byStatus.putAll(grouped("""
                SELECT status AS k, count(*) AS v FROM registrations
                WHERE is_deleted = false AND created_at >= :from AND created_at < :to GROUP BY status
                """, p));
        return StatsPayloads.Registrations.builder()
                .total(byStatus.values().stream().mapToLong(Long::longValue).sum())
                .byStatus(byStatus)
                .build();
    }

    private StatsPayloads.Funnel funnel(MapSqlParameterSource p) {
        String window = "FROM registrations WHERE is_deleted = false AND created_at >= :from AND created_at < :to";
        long submitted = count("SELECT count(*) " + window, p);
        long accepted = count("SELECT count(*) " + window + " AND " + ACCEPTED, p);
        long paid = count("SELECT count(*) " + window + " AND " + PAID, p);
        return StatsPayloads.Funnel.builder()
                .submitted(submitted).accepted(accepted).paid(paid)
                .acceptanceRate(rate(accepted, submitted))
                .paymentRate(rate(paid, accepted))
                .conversionRate(rate(paid, submitted))
                .build();
    }

    private StatsPayloads.Revenue revenue(MapSqlParameterSource p) {
        long collected = count("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE is_deleted = false AND status = 'CONFIRMED' AND confirmed_at >= :from AND confirmed_at < :to
                """, p);
        long refunded = count("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE is_deleted = false AND status = 'REFUNDED' AND refunded_at >= :from AND refunded_at < :to
                """, p);
        long collectedPayments = count("""
                SELECT count(*) FROM payments
                WHERE is_deleted = false AND status = 'CONFIRMED' AND confirmed_at >= :from AND confirmed_at < :to
                """, p);
        long outstanding = count("""
                SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN registrations r ON r.id = p.registration_id
                WHERE p.is_deleted = false AND p.status IN ('PENDING','DECLARED') AND r.is_deleted = false
                  AND r.status IN ('PAYMENT_PENDING','PAYMENT_TO_CONFIRM','CONFIRMED')
                """, p);
        long overdue = count("""
                SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN registrations r ON r.id = p.registration_id
                WHERE p.is_deleted = false AND p.status = 'PENDING' AND p.due_date < CURRENT_DATE AND r.is_deleted = false
                  AND r.status IN ('PAYMENT_PENDING','CONFIRMED')
                """, p);
        Map<String, Long> byMethod = grouped("""
                SELECT COALESCE(method, 'NON_RENSEIGNE') AS k, COALESCE(SUM(amount), 0) AS v FROM payments
                WHERE is_deleted = false AND status = 'CONFIRMED' AND confirmed_at >= :from AND confirmed_at < :to GROUP BY method
                """, p);
        return StatsPayloads.Revenue.builder()
                .collected(collected).refunded(refunded).net(collected - refunded)
                .outstanding(outstanding).overdue(overdue)
                .averagePayment(collectedPayments == 0 ? null : Math.round((double) collected / collectedPayments))
                .collectedByMethod(byMethod)
                .build();
    }

    private List<StatsPayloads.MonthlyPoint> monthly(LocalDate start, LocalDate end, MapSqlParameterSource p) {
        Map<String, Long> registrations = grouped("""
                SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS k, count(*) AS v FROM registrations
                WHERE is_deleted = false AND created_at >= :from AND created_at < :to GROUP BY 1
                """, p);
        Map<String, Long> collected = grouped("""
                SELECT to_char(date_trunc('month', confirmed_at), 'YYYY-MM') AS k, COALESCE(SUM(amount), 0) AS v FROM payments
                WHERE is_deleted = false AND status = 'CONFIRMED' AND confirmed_at >= :from AND confirmed_at < :to GROUP BY 1
                """, p);
        List<StatsPayloads.MonthlyPoint> points = new ArrayList<>();
        for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
            String key = m.toString();
            points.add(StatsPayloads.MonthlyPoint.builder()
                    .month(key).registrations(registrations.getOrDefault(key, 0L)).collected(collected.getOrDefault(key, 0L)).build());
        }
        return points;
    }

    private List<StatsPayloads.SessionFill> sessions() {
        return jdbc.query("""
                SELECT s.id, s.session_name, b.title, s.start_date, s.status, s.max_participants, s.current_participants,
                       (SELECT count(*) FROM registrations r WHERE r.bootcamp_session_id = s.id AND r.is_deleted = false
                          AND r.status IN ('PENDING','PAYMENT_PENDING','PAYMENT_TO_CONFIRM')) AS pending
                FROM bootcamp_sessions s JOIN bootcamps b ON b.id = s.bootcamp_id
                WHERE s.is_deleted = false AND s.status IN ('OPEN','UPCOMING','IN_PROGRESS')
                ORDER BY s.start_date NULLS LAST, s.session_name LIMIT 20
                """, new MapSqlParameterSource(), (rs, i) -> {
            int capacity = rs.getInt("max_participants");
            int confirmed = rs.getInt("current_participants");
            java.sql.Date start = rs.getDate("start_date");
            return StatsPayloads.SessionFill.builder()
                    .sessionId(rs.getObject("id", UUID.class))
                    .sessionName(rs.getString("session_name"))
                    .formationTitle(rs.getString("title"))
                    .startDate(start != null ? start.toLocalDate() : null)
                    .status(rs.getString("status"))
                    .capacity(capacity).confirmed(confirmed).pending(rs.getLong("pending"))
                    .fillRate(capacity > 0 ? (int) Math.round(confirmed * 100.0 / capacity) : null)
                    .build();
        });
    }

    private List<StatsPayloads.FormationStat> topFormations(MapSqlParameterSource p) {
        Map<UUID, Long> collectedByFormation = new HashMap<>();
        jdbc.query("""
                SELECT r.bootcamp_id AS id, COALESCE(SUM(pay.amount), 0) AS total FROM payments pay
                JOIN registrations r ON r.id = pay.registration_id
                WHERE pay.is_deleted = false AND pay.status = 'CONFIRMED' AND pay.confirmed_at >= :from AND pay.confirmed_at < :to
                  AND r.bootcamp_id IS NOT NULL GROUP BY r.bootcamp_id
                """, p, rs -> {
            collectedByFormation.put(rs.getObject("id", UUID.class), rs.getLong("total"));
        });
        return jdbc.query("""
                SELECT b.id, b.title, count(*) AS registrations, count(*) FILTER (WHERE r.status IN ('CONFIRMED','COMPLETED')) AS paid
                FROM registrations r JOIN bootcamps b ON b.id = r.bootcamp_id
                WHERE r.is_deleted = false AND r.created_at >= :from AND r.created_at < :to
                GROUP BY b.id, b.title ORDER BY registrations DESC, b.title LIMIT 10
                """, p, (rs, i) -> StatsPayloads.FormationStat.builder()
                .formationId(rs.getObject("id", UUID.class)).title(rs.getString("title"))
                .registrations(rs.getLong("registrations")).paid(rs.getLong("paid"))
                .collected(collectedByFormation.getOrDefault(rs.getObject("id", UUID.class), 0L))
                .build());
    }

    private StatsPayloads.Breakdowns breakdowns(MapSqlParameterSource p) {
        String window = "FROM registrations WHERE is_deleted = false AND created_at >= :from AND created_at < :to";
        return StatsPayloads.Breakdowns.builder()
                .bySource(grouped("SELECT source AS k, count(*) AS v " + window + " GROUP BY source ORDER BY v DESC", p))
                .byProfile(grouped("SELECT COALESCE(profile, 'NON_RENSEIGNE') AS k, count(*) AS v " + window + " GROUP BY profile ORDER BY v DESC", p))
                .byCountry(grouped("SELECT COALESCE(NULLIF(trim(country), ''), 'NON_RENSEIGNE') AS k, count(*) AS v " + window
                        + " GROUP BY 1 ORDER BY v DESC LIMIT 10", p))
                .byPromoCode(grouped("SELECT promo_code_used AS k, count(*) AS v " + window
                        + " AND promo_code_used IS NOT NULL GROUP BY promo_code_used ORDER BY v DESC LIMIT 10", p))
                .build();
    }

    private StatsPayloads.Learners learners(MapSqlParameterSource p) {
        return StatsPayloads.Learners.builder()
                .total(count("SELECT count(*) FROM learners WHERE is_deleted = false", p))
                .newInPeriod(count("SELECT count(*) FROM learners WHERE is_deleted = false AND created_at >= :from AND created_at < :to", p))
                .activeEnrollments(count("SELECT count(*) FROM enrollments WHERE is_deleted = false AND status = 'ACTIVE'", p))
                .certificatesIssued(count("""
                        SELECT count(*) FROM certificates WHERE is_deleted = false AND status = 'VALID'
                          AND issued_at >= :from AND issued_at < :to
                        """, p))
                .certificatesTotal(count("SELECT count(*) FROM certificates WHERE is_deleted = false AND status = 'VALID'", p))
                .build();
    }

    private StatsPayloads.Audience audience(MapSqlParameterSource p) {
        return StatsPayloads.Audience.builder()
                .newsletterSubscribers(count("SELECT count(*) FROM newsletter_subscriptions WHERE is_deleted = false AND status = 'CONFIRMED'", p))
                .newsletterNewInPeriod(count("""
                        SELECT count(*) FROM newsletter_subscriptions WHERE is_deleted = false AND status = 'CONFIRMED'
                          AND confirmed_at >= :from AND confirmed_at < :to
                        """, p))
                .contactMessagesByType(grouped("""
                        SELECT type AS k, count(*) AS v FROM contact_messages
                        WHERE is_deleted = false AND created_at >= :from AND created_at < :to GROUP BY type ORDER BY v DESC
                        """, p))
                .build();
    }

    // ── Outils SQL ───────────────────────────────────────────────────

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    /** Résultat {@code k, v} → table ordonnée comme la requête. */
    private Map<String, Long> grouped(String sql, MapSqlParameterSource params) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getString("k"), rs.getLong("v"));
        });
        return result;
    }

    private Double rate(long part, long total) {
        return total == 0 ? null : Math.round(part * 1000.0 / total) / 10.0;
    }
}
