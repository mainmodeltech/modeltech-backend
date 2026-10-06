package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartnerRepository extends SoftDeleteRepository<Partner, UUID> {

    // Public : partenaires actifs ayant au moins une formation publiée
    @Query("""
        SELECT p FROM Partner p
        WHERE p.active = true
          AND p.isDeleted = false
          AND EXISTS (
              SELECT 1 FROM Bootcamp b
              WHERE b.partner = p AND b.published = true AND b.isDeleted = false
          )
        ORDER BY p.name ASC
    """)
    List<Partner> findAllPublic();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
