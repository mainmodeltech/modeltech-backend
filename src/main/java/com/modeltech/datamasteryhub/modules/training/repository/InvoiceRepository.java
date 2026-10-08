package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends SoftDeleteRepository<Invoice, UUID> {

    Optional<Invoice> findByNumberAndIsDeletedFalse(String number);

    Optional<Invoice> findByRegistrationIdAndStatusAndIsDeletedFalse(UUID registrationId, String status);

    List<Invoice> findAllByRegistrationIdAndIsDeletedFalseOrderByIssueDateDescCreatedAtDesc(UUID registrationId);

    @Query(value = """
            SELECT i FROM Invoice i JOIN FETCH i.registration
            WHERE i.isDeleted = false AND (:status IS NULL OR i.status = :status)
            """,
            countQuery = "SELECT count(i) FROM Invoice i WHERE i.isDeleted = false AND (:status IS NULL OR i.status = :status)")
    Page<Invoice> search(@Param("status") String status, Pageable pageable);
}
