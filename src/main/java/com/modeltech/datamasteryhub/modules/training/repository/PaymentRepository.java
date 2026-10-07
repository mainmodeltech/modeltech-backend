package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends SoftDeleteRepository<Payment, UUID> {

    Optional<Payment> findByPublicTokenAndIsDeletedFalse(String publicToken);

    List<Payment> findAllByRegistrationIdAndIsDeletedFalseOrderByInstallmentNumberAsc(UUID registrationId);

    List<Payment> findAllByRegistrationIdInAndIsDeletedFalse(Collection<UUID> registrationIds);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.isDeleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:registrationId IS NULL OR p.registration.id = :registrationId)
            """)
    Page<Payment> search(@Param("status") PaymentStatus status,
                         @Param("registrationId") UUID registrationId,
                         Pageable pageable);

    /** Échéances à relancer : en attente, sur une inscription encore active. */
    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.registration r
            WHERE p.isDeleted = false AND r.isDeleted = false
              AND p.status = com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus.PENDING
              AND r.status IN (com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus.PAYMENT_PENDING,
                               com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus.CONFIRMED)
            """)
    List<Payment> findRemindable();
}
