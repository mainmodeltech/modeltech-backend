package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.CourseModule;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseModuleRepository extends SoftDeleteRepository<CourseModule, UUID> {

    List<CourseModule> findAllByBootcampIdAndIsDeletedFalseOrderByPositionAsc(UUID bootcampId);
}
