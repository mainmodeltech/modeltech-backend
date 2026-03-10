package com.modeltech.datamasteryhub.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

/**
 * Enveloppe standard pour toutes les réponses API.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Opération réussie",
 *   "data": { ... },
 *   "pagination": { ... }   // présent uniquement si fourni
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final PaginationMeta pagination;

    // ── Factories ──────────────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data, PaginationMeta pagination) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .pagination(pagination)
                .build();
    }

    /**
     * Factory dédiée aux réponses paginées.
     * Extrait automatiquement le content du Page Spring pour éviter
     * d'exposer la structure interne (pageable, sort, etc.).
     *
     * Résultat :
     * { "success": true, "data": [...], "pagination": { "page": 0, "size": 20, ... } }
     */
    public static <T> ApiResponse<java.util.List<T>> page(String message, Page<T> pageResult) {
        PaginationMeta pagination = PaginationMeta.builder()
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
        return ApiResponse.<java.util.List<T>>builder()
                .success(true)
                .message(message)
                .data(pageResult.getContent())
                .pagination(pagination)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    // ── Pagination meta ────────────────────────────────────────────────────

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaginationMeta {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
    }
}