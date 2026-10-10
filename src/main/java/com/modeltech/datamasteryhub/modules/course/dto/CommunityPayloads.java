package com.modeltech.datamasteryhub.modules.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Fil d'entraide d'une formation. */
public final class CommunityPayloads {

    private CommunityPayloads() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Post {
        private UUID id;
        /** Prénom et initiale du nom : les e-mails ne sont jamais exposés aux autres apprenants. */
        private String author;
        /** true pour l'auteur du message (peut le supprimer). */
        private boolean mine;
        private String content;
        private LocalDateTime createdAt;
        private List<Post> replies;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ThreadPage {
        private List<Post> threads;
        private long totalThreads;
        private int page;
        private int totalPages;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateRequest {
        @NotBlank(message = "Le message est obligatoire")
        @Size(max = 2000, message = "2 000 caractères maximum")
        private String content;

        /** Message auquel on répond ; absent pour un nouveau message. */
        private UUID parentId;
    }

    /** Vue de modération : l'équipe voit le nom complet et l'e-mail de l'auteur. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModerationPost {
        private UUID id;
        private UUID parentId;
        private String authorName;
        private String authorEmail;
        private String content;
        private LocalDateTime createdAt;
    }
}
