package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseProjectRepository extends SoftDeleteRepository<CourseProject, UUID> {

    Optional<CourseProject> findByBootcampId(UUID bootcampId);
}
