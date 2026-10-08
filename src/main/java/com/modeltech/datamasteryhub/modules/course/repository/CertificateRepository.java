package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.Certificate;
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
public interface CertificateRepository extends SoftDeleteRepository<Certificate, UUID> {

    Optional<Certificate> findByPublicIdAndIsDeletedFalse(String publicId);

    boolean existsByLearnerIdAndBootcampIdAndIsDeletedFalse(UUID learnerId, UUID bootcampId);

    Optional<Certificate> findByLearnerIdAndBootcampIdAndStatusAndIsDeletedFalse(UUID learnerId, UUID bootcampId, String status);

    List<Certificate> findAllByLearnerIdAndIsDeletedFalseOrderByIssuedAtDesc(UUID learnerId);

    List<Certificate> findAllByBootcampIdAndLearnerIdInAndStatusAndIsDeletedFalse(UUID bootcampId, Collection<UUID> learnerIds, String status);

    @Query(value = """
            SELECT c FROM Certificate c
            JOIN FETCH c.learner
            WHERE c.isDeleted = false AND (:status IS NULL OR c.status = :status)
            """,
            countQuery = "SELECT count(c) FROM Certificate c WHERE c.isDeleted = false AND (:status IS NULL OR c.status = :status)")
    Page<Certificate> search(@Param("status") String status, Pageable pageable);
}
