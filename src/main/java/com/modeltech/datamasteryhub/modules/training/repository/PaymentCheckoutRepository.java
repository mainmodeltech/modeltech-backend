package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.modules.training.entity.PaymentCheckout;
import com.modeltech.datamasteryhub.modules.training.enums.CheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCheckoutRepository extends JpaRepository<PaymentCheckout, UUID> {

    @EntityGraph(attributePaths = {"registration", "payments"})
    Optional<PaymentCheckout> findByPublicRef(UUID publicRef);

    Optional<PaymentCheckout> findByProviderToken(String providerToken);

    /** Verrouille la tentative le temps de la vérifier : une notification et un contrôle simultanés ne confirment qu'une fois. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PaymentCheckout c WHERE c.id = :id")
    Optional<PaymentCheckout> lockById(@Param("id") UUID id);

    List<PaymentCheckout> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(CheckoutStatus status, LocalDateTime before, Pageable pageable);

    /** Tentatives encore ouvertes d'une inscription, pour ne pas multiplier les liens de paiement. */
    @EntityGraph(attributePaths = "payments")
    List<PaymentCheckout> findByRegistrationIdAndStatusOrderByCreatedAtDesc(UUID registrationId, CheckoutStatus status);

    /** Tentatives récentes d'une inscription (plafond d'essais par heure). */
    long countByRegistrationIdAndCreatedAtAfter(UUID registrationId, LocalDateTime after);
}
