package com.modeltech.datamasteryhub.modules.legal.repository;

import com.modeltech.datamasteryhub.modules.legal.LegalCode;
import com.modeltech.datamasteryhub.modules.legal.LegalStatus;
import com.modeltech.datamasteryhub.modules.legal.entity.LegalDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findByCodeAndStatus(LegalCode code, LegalStatus status);

    List<LegalDocument> findByStatus(LegalStatus status);

    List<LegalDocument> findByCodeOrderByVersionDesc(LegalCode code);

    boolean existsByCode(LegalCode code);

    @Query("SELECT COALESCE(MAX(d.version), 0) FROM LegalDocument d WHERE d.code = :code")
    int maxVersion(@Param("code") LegalCode code);

    /** Verrouille la ligne du brouillon pendant sa publication (deux publications simultanées ne peuvent pas se croiser). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM LegalDocument d WHERE d.code = :code AND d.status = com.modeltech.datamasteryhub.modules.legal.LegalStatus.DRAFT")
    Optional<LegalDocument> lockDraft(@Param("code") LegalCode code);
}
