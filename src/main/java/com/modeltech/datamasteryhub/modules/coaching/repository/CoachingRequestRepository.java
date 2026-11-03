package com.modeltech.datamasteryhub.modules.coaching.repository;

import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingRequest;
import com.modeltech.datamasteryhub.modules.coaching.entity.CoachingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachingRequestRepository extends JpaRepository<CoachingRequest, UUID> {

    Optional<CoachingRequest> findByPayToken(String payToken);

    /** Verrouille la demande pendant une transition ou une vérification de paiement (un seul gagnant en cas de course). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CoachingRequest r WHERE r.id = :id")
    Optional<CoachingRequest> lockById(@Param("id") UUID id);

    Page<CoachingRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CoachingRequest> findAllByStatusOrderByCreatedAtDesc(CoachingStatus status, Pageable pageable);

    long countByStatus(CoachingStatus status);

    /** Plafond de demandes par adresse (anti-abus du formulaire public). */
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, LocalDateTime after);

    /** Liens de paiement arrivés à échéance sans paiement. */
    List<CoachingRequest> findByStatusInAndTokenExpiresAtBefore(List<CoachingStatus> statuses, LocalDateTime before);
}
