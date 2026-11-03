package com.modeltech.datamasteryhub.modules.legal.repository;

import com.modeltech.datamasteryhub.modules.legal.entity.LegalAcceptance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, UUID> {

    /** Documents déjà acceptés pour un acte précis (page de paiement, commande…). */
    @Query("SELECT a.document.id FROM LegalAcceptance a WHERE a.subjectType = :type AND a.subjectRef = :ref")
    List<UUID> acceptedDocumentIds(@Param("type") String subjectType, @Param("ref") String subjectRef);

    /** Documents déjà acceptés par une personne, quel que soit l'acte (un apprenant n'est pas redemandé pour une version qu'il a déjà acceptée). */
    @Query("SELECT DISTINCT a.document.id FROM LegalAcceptance a WHERE lower(a.email) = lower(:email) AND a.document.id IN :ids")
    List<UUID> acceptedByEmail(@Param("email") String email, @Param("ids") Collection<UUID> documentIds);

    @Query("""
            SELECT a FROM LegalAcceptance a
            WHERE (:email IS NULL OR lower(a.email) LIKE lower(concat('%', cast(:email as string), '%')))
              AND (:code IS NULL OR a.documentCode = :code)
            ORDER BY a.acceptedAt DESC
            """)
    Page<LegalAcceptance> search(@Param("email") String email, @Param("code") String code, Pageable pageable);
}
