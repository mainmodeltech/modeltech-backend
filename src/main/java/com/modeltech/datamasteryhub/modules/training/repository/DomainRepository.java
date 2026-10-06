package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DomainRepository extends SoftDeleteRepository<Domain, UUID> {

    // Public : domaines visibles, dans l'ordre d'affichage
    List<Domain> findAllByVisibleTrueAndIsDeletedFalseOrderByDisplayOrderAscNameAsc();

    // Unicité du slug (soft-deleted inclus : le slug reste réservé)
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
