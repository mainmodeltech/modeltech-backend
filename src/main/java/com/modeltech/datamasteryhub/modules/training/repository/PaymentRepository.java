package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends SoftDeleteRepository<Payment, UUID> {

    List<Payment> findAllByRegistrationIdAndIsDeletedFalseOrderByPaymentDateAsc(UUID registrationId);

    /**
     * Somme des montants payes pour une inscription (hors soft-deleted).
     * Retourne null si aucun paiement → coalesce cote appelant.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
        "WHERE p.registration.id = :registrationId AND p.isDeleted = false"
    )
    int sumAmountByRegistrationId(UUID registrationId);
}
