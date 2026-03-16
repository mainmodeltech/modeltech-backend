package com.modeltech.datamasteryhub.modules.networking.service;

import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.ProjectMemberRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectScreenshotResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    // Admin - Projet CRUD
    ProjectResponse   create(CreateProjectRequest request);
    Page<ProjectResponse> findAllForAdmin(Pageable pageable);
    ProjectResponse   findByIdForAdmin(UUID id);
    ProjectResponse   update(UUID id, UpdateProjectRequest request);
    void              softDelete(UUID id);

    // Admin - Membres
    ProjectResponse   addMember(UUID projectId, ProjectMemberRequest request);
    ProjectResponse   removeMember(UUID projectId, UUID alumniId);

    // Admin - Screenshots (MinIO)
    ProjectScreenshotResponse addScreenshot(UUID projectId, MultipartFile file, String caption, int displayOrder);
    void                      deleteScreenshot(UUID projectId, UUID screenshotId);

    // Admin - Cover image
    ProjectResponse   uploadCoverImage(UUID projectId, MultipartFile file);

    // Public
    List<ProjectSummaryResponse> findAllPublished();
    ProjectResponse findPublishedById(UUID id);
}

