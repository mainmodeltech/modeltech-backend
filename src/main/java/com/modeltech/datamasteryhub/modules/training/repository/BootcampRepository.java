package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BootcampRepository extends JpaRepository<Bootcamp, UUID> {

    // Public : uniquement les publiés et non supprimés
    List<Bootcamp> findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();

    // Admin : tous (hors soft-deleted)
    List<Bootcamp> findAllByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc();

    // Détail public
    Optional<Bootcamp> findByIdAndPublishedTrueAndIsDeletedFalse(UUID id);

    Optional<Bootcamp> findByIdAndIsDeletedFalse(UUID id);

    // ── Catalogue par domaines ───────────────────────────────────────
    // Formations publiées, rattachées à un domaine visible (le front exige `domain`)
    @Query("""
        SELECT DISTINCT b FROM Bootcamp b
        JOIN FETCH b.domain d
        LEFT JOIN FETCH b.partner
        WHERE b.published = true AND b.isDeleted = false
          AND d.visible = true AND d.isDeleted = false
        ORDER BY b.displayOrder ASC, b.createdAt DESC
    """)
    List<Bootcamp> findAllForCatalogue();

    @Query("""
        SELECT b FROM Bootcamp b
        JOIN FETCH b.domain d
        LEFT JOIN FETCH b.partner
        WHERE b.slug = :slug
          AND b.published = true AND b.isDeleted = false
          AND d.visible = true AND d.isDeleted = false
    """)
    Optional<Bootcamp> findForCatalogueBySlug(@Param("slug") String slug);

    // Repli « formations liées » : autres formations publiées du même domaine
    List<Bootcamp> findTop3ByDomainIdAndIdNotAndPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc(UUID domainId, UUID id);

    // Unicité du slug (soft-deleted inclus : le slug reste réservé)
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);

    // Garde-fous de suppression d'un domaine / partenaire
    boolean existsByDomainIdAndIsDeletedFalse(UUID domainId);
    boolean existsByPartnerIdAndIsDeletedFalse(UUID partnerId);

    // Check unicité du titre
    boolean existsByTitleIgnoreCaseAndIsDeletedFalse(String title);
    boolean existsByTitleIgnoreCaseAndIsDeletedFalseAndIdNot(String title, UUID id);
}
