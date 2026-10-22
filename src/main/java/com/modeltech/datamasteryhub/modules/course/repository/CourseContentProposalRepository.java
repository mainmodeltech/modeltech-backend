package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.modules.course.entity.CourseContentProposal;
import com.modeltech.datamasteryhub.modules.course.enums.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseContentProposalRepository extends JpaRepository<CourseContentProposal, UUID> {

    Optional<CourseContentProposal> findByIdAndIsDeletedFalse(UUID id);

    /** La proposition en cours (au plus une : index unique partiel). */
    Optional<CourseContentProposal> findFirstByBootcampIdAndAuthorIdAndStatusInAndIsDeletedFalse(
            UUID bootcampId, UUID authorId, Collection<ProposalStatus> statuses);

    /** La dernière proposition de cet auteur pour cette formation, quel que soit son état. */
    Optional<CourseContentProposal> findFirstByBootcampIdAndAuthorIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID bootcampId, UUID authorId);

    List<CourseContentProposal> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    List<CourseContentProposal> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ProposalStatus status);

    /** Propositions soumises qu'une autre personne que {@code email} doit relire (pastille du menu). */
    @Query("SELECT count(p) FROM CourseContentProposal p WHERE p.isDeleted = false AND p.status = :status AND lower(p.author.email) <> lower(:email)")
    long countToReview(@Param("status") ProposalStatus status, @Param("email") String email);
}
