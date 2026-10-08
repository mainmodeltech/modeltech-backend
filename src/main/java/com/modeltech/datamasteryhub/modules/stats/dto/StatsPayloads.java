package com.modeltech.datamasteryhub.modules.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Indicateurs du tableau de bord d'administration. Montants en unité de la devise (XOF), jamais arrondis par le serveur. */
public final class StatsPayloads {

    private StatsPayloads() {}

    /** Ce qui attend l'équipe aujourd'hui : un compteur par file de travail. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Actions {
        /** Candidatures à examiner (PENDING). */
        private long newApplications;
        /** Paiements déclarés à vérifier (DECLARED). */
        private long paymentsToConfirm;
        /** Échéances en attente dont la date est dépassée. */
        private long overdueInstallments;
        /** Messages de contact et demandes non lus. */
        private long unreadMessages;
        /** Questions d'apprenants sans réponse. */
        private long openQuestions;
        /** Projets finaux rendus, à corriger. */
        private long projectsToReview;
        /** Lives dans les 7 prochains jours. */
        private long upcomingLives;
        /** E-mails en échec ces 7 derniers jours. */
        private long failedEmails;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Overview {
        private LocalDate from;
        private LocalDate to;
        private String currency;
        private Registrations registrations;
        private Funnel funnel;
        private Revenue revenue;
        private List<MonthlyPoint> monthly;
        private List<SessionFill> sessions;
        private List<FormationStat> topFormations;
        private Breakdowns breakdowns;
        private Learners learners;
        private Audience audience;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Registrations {
        private long total;
        /** Nombre par statut (PENDING, PAYMENT_PENDING, …) ; les statuts absents valent 0. */
        private Map<String, Long> byStatus;
    }

    /** Parcours des candidatures déposées sur la période. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Funnel {
        private long submitted;
        private long accepted;
        private long paid;
        /** accepted / submitted, en %, ou null sans candidature. */
        private Double acceptanceRate;
        /** paid / accepted, en %. */
        private Double paymentRate;
        /** paid / submitted, en %. */
        private Double conversionRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Revenue {
        /** Encaissé sur la période (échéances confirmées). */
        private long collected;
        /** Remboursé sur la période. */
        private long refunded;
        /** collected − refunded. */
        private long net;
        /** Reste à encaisser (échéances en attente ou déclarées), toutes périodes. */
        private long outstanding;
        /** Part de {@code outstanding} dont l'échéance est dépassée. */
        private long overdue;
        /** Encaissé moyen par échéance confirmée. */
        private Long averagePayment;
        private Map<String, Long> collectedByMethod;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlyPoint {
        /** {@code yyyy-MM}. */
        private String month;
        private long registrations;
        private long collected;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SessionFill {
        private UUID sessionId;
        private String sessionName;
        private String formationTitle;
        private LocalDate startDate;
        private String status;
        private int capacity;
        /** Inscrits confirmés. */
        private int confirmed;
        /** Candidatures en cours de traitement pour cette session. */
        private long pending;
        /** confirmed / capacity, en %, ou null si la capacité est inconnue. */
        private Integer fillRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FormationStat {
        private UUID formationId;
        private String title;
        private long registrations;
        private long paid;
        private long collected;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Breakdowns {
        private Map<String, Long> bySource;
        private Map<String, Long> byProfile;
        private Map<String, Long> byCountry;
        private Map<String, Long> byPromoCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Learners {
        private long total;
        private long newInPeriod;
        private long activeEnrollments;
        private long certificatesIssued;
        private long certificatesTotal;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Audience {
        private long newsletterSubscribers;
        private long newsletterNewInPeriod;
        private Map<String, Long> contactMessagesByType;
    }
}
