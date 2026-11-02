package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.OrderStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
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

public interface SimOrderRepository extends JpaRepository<SimOrder, UUID> {

    @EntityGraph(attributePaths = "learner")
    Optional<SimOrder> findByPublicRef(UUID publicRef);

    @EntityGraph(attributePaths = "learner")
    Optional<SimOrder> findByProviderToken(String providerToken);

    @EntityGraph(attributePaths = "learner")
    Optional<SimOrder> findDetailedById(UUID id);

    /** Verrouille la commande le temps de la vérifier : une notification et un contrôle simultanés ne peuvent pas ouvrir deux accès. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SimOrder o JOIN FETCH o.learner WHERE o.id = :id")
    Optional<SimOrder> lockById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "learner")
    Page<SimOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "learner")
    Page<SimOrder> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    long countByLearnerIdAndStatusAndCreatedAtAfter(UUID learnerId, OrderStatus status, LocalDateTime after);

    /** Commandes encore en attente depuis un moment : à revérifier (notification perdue, client parti avant le retour). */
    List<SimOrder> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus status, LocalDateTime before, Pageable pageable);
}
