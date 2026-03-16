package com.modeltech.datamasteryhub.modules.networking.repository;

import com.modeltech.datamasteryhub.modules.networking.entity.ProjectScreenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectScreenshotRepository extends JpaRepository<ProjectScreenshot, UUID> {

    List<ProjectScreenshot> findAllByProjectIdOrderByDisplayOrderAsc(UUID projectId);
}
