package com.modeltech.datamasteryhub.modules.communication.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class MasterclassPayloads {

    private MasterclassPayloads() {}

    /** Ce que voit le public : jamais le lien de connexion. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PublicEvent {
        private String code;
        private String title;
        private String description;
        private LocalDateTime startsAt;
        private int durationMinutes;
        private String format;
        private String place;
        /** Places restantes ; null = illimité. */
        private Integer spotsLeft;
        private boolean full;
        /** Faux si l'événement est fini, complet ou fermé aux inscriptions. */
        private boolean registrationOpen;
    }

    /** Vue de l'équipe. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdminEvent {
        private UUID id;
        private String code;
        private String title;
        private String description;
        private LocalDateTime startsAt;
        private int durationMinutes;
        private String format;
        private String place;
        private String joinUrl;
        private Integer capacity;
        private boolean published;
        private boolean registrationsClosed;
        private long registered;
        private long attended;
        /** DRAFT (non publiée), UPCOMING, OPEN (inscriptions ouvertes), CLOSED (fermée/complète), DONE (terminée). */
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EventRequest {
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200)
        private String title;

        @Size(max = 5000)
        private String description;

        @NotNull(message = "La date et l'heure sont obligatoires")
        private LocalDateTime startsAt;

        @Min(value = 15, message = "15 minutes au moins") @Max(value = 600, message = "10 heures au plus")
        private int durationMinutes = 120;

        @Pattern(regexp = "ONLINE|ONSITE|HYBRID", message = "Format : ONLINE, ONSITE ou HYBRID")
        private String format = "ONLINE";

        @Size(max = 255)
        private String place;

        @Size(max = 500)
        @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "Le lien doit commencer par http:// ou https://")
        private String joinUrl;

        @Min(value = 1, message = "Au moins une place") @Max(value = 100000)
        private Integer capacity;

        private boolean published;
        private boolean registrationsClosed;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AttendanceRequest {
        @NotBlank @Pattern(regexp = "REGISTERED|ATTENDED|ABSENT", message = "REGISTERED, ATTENDED ou ABSENT")
        private String attendance;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MessageRequest {
        @NotBlank(message = "L'objet est obligatoire") @Size(max = 150)
        private String subject;

        @NotBlank(message = "Le message est obligatoire") @Size(max = 5000)
        private String body;

        /** ALL (tous les inscrits), ATTENDED (présents), ABSENT (absents). */
        @Pattern(regexp = "ALL|ATTENDED|ABSENT", message = "ALL, ATTENDED ou ABSENT")
        private String audience = "ALL";
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageResult {
        private int recipients;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Upcoming {
        private List<PublicEvent> events;
    }
}
