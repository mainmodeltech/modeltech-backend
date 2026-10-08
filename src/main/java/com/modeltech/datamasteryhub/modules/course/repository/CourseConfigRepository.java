package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.CourseConfig;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseConfigRepository extends SoftDeleteRepository<CourseConfig, UUID> {

    Optional<CourseConfig> findByBootcampId(UUID bootcampId);
}
