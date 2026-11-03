package com.modeltech.datamasteryhub.modules.coaching.repository;

import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingCheckout;
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

public interface CoachingCheckoutRepository extends JpaRepository<CoachingCheckout, UUID> {

    @EntityGraph(attributePaths = "request")
    Optional<CoachingCheckout> findByPublicRef(UUID publicRef);

    Optional<CoachingCheckout> findByProviderToken(String providerToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CoachingCheckout c WHERE c.id = :id")
    Optional<CoachingCheckout> lockById(@Param("id") UUID id);

    List<CoachingCheckout> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(CheckoutStatus status, LocalDateTime before, Pageable pageable);

    List<CoachingCheckout> findByRequestIdAndStatusOrderByCreatedAtDesc(UUID requestId, CheckoutStatus status);

    long countByRequestIdAndCreatedAtAfter(UUID requestId, LocalDateTime after);
}
