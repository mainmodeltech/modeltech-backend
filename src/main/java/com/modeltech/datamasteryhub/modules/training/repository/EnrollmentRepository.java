package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends SoftDeleteRepository<Enrollment, UUID> {

    Optional<Enrollment> findByRegistrationIdAndIsDeletedFalse(UUID registrationId);
}
