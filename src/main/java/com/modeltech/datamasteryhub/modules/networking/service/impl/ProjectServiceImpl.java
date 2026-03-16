package com.modeltech.datamasteryhub.modules.networking.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.ProjectMemberRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateProjectRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectScreenshotResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.ProjectSummaryResponse;
import com.modeltech.datamasteryhub.modules.networking.entity.Alumni;
import com.modeltech.datamasteryhub.modules.networking.entity.Project;
import com.modeltech.datamasteryhub.modules.networking.entity.ProjectMember;
import com.modeltech.datamasteryhub.modules.networking.entity.ProjectScreenshot;
import com.modeltech.datamasteryhub.modules.networking.mapper.ProjectMapper;
import com.modeltech.datamasteryhub.modules.networking.repository.AlumniRepository;
import com.modeltech.datamasteryhub.modules.networking.repository.ProjectMemberRepository;
import com.modeltech.datamasteryhub.modules.networking.repository.ProjectRepository;
import com.modeltech.datamasteryhub.modules.networking.repository.ProjectScreenshotRepository;
import com.modeltech.datamasteryhub.modules.networking.service.ProjectService;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository            projectRepository;
    private final AlumniRepository             alumniRepository;
    private final ProjectMemberRepository      memberRepository;
    private final ProjectScreenshotRepository  screenshotRepository;
    private final ProjectMapper                projectMapper;
    private final StorageService storageService;

    // ─── Projet CRUD ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        Project project = projectMapper.toEntity(request);
        Project saved   = projectRepository.save(project);

        // Membres : au moins un obligatoire (validé par @NotEmpty sur le DTO)
        request.getMembers().forEach(m -> addMemberToProject(saved, m));

        log.info("Projet créé : {} (id={})", saved.getTitle(), saved.getId());
        return projectMapper.toResponse(projectRepository.findByIdAndIsDeletedFalse(saved.getId())
                .orElseThrow());
    }

    @Override
    public Page<ProjectResponse> findAllForAdmin(Pageable pageable) {
        return projectRepository.findAllByIsDeletedFalse(pageable)
                .map(projectMapper::toResponse);
    }

    @Override
    public ProjectResponse findByIdForAdmin(UUID id) {
        return projectMapper.toResponse(findActiveOrThrow(id));
    }

    @Override
    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        Project project = findActiveOrThrow(id);
        projectMapper.updateEntityFromRequest(request, project);
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Project project = findActiveOrThrow(id);

        // Supprimer les screenshots MinIO avant soft delete
        project.getScreenshots().forEach(s -> storageService.delete(s.getObjectKey()));

        project.setDeleted(true);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        log.info("Projet supprimé (soft) : {}", id);
    }

    // ─── Membres ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectResponse addMember(UUID projectId, ProjectMemberRequest request) {
        Project project = findActiveOrThrow(projectId);

        if (memberRepository.existsByProjectIdAndAlumniId(projectId, request.getAlumniId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet alumni est déjà membre de ce projet.");
        }

        addMemberToProject(project, request);
        return projectMapper.toResponse(findActiveOrThrow(projectId));
    }

    @Override
    @Transactional
    public ProjectResponse removeMember(UUID projectId, UUID alumniId) {
        Project project = findActiveOrThrow(projectId);

        // Contrainte : ne pas supprimer le dernier membre
        if (memberRepository.countByProjectId(projectId) <= 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Un projet doit conserver au moins un membre.");
        }

        memberRepository.findByProjectIdAndAlumniId(projectId, alumniId)
                .ifPresent(memberRepository::delete);

        return projectMapper.toResponse(findActiveOrThrow(projectId));
    }

    // ─── Screenshots ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectScreenshotResponse addScreenshot(UUID projectId, MultipartFile file,
                                                   String caption, int displayOrder) {
        Project project = findActiveOrThrow(projectId);

        StorageService.UploadResult result = storageService.upload(file, "projects/screenshots");

        ProjectScreenshot screenshot = ProjectScreenshot.builder()
                .project(project)
                .photoUrl(result.url())
                .objectKey(result.objectKey())
                .caption(caption)
                .displayOrder(displayOrder)
                .build();

        ProjectScreenshot saved = screenshotRepository.save(screenshot);
        log.info("Screenshot ajouté au projet {} : {}", projectId, result.objectKey());
        return projectMapper.toScreenshotResponse(saved);
    }

    @Override
    @Transactional
    public void deleteScreenshot(UUID projectId, UUID screenshotId) {
        ProjectScreenshot screenshot = screenshotRepository.findById(screenshotId)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Screenshot", "id", screenshotId));

        storageService.delete(screenshot.getObjectKey());
        screenshotRepository.delete(screenshot);
        log.info("Screenshot supprimé : {}", screenshotId);
    }

    // ─── Cover image ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectResponse uploadCoverImage(UUID projectId, MultipartFile file) {
        Project project = findActiveOrThrow(projectId);

        StorageService.UploadResult result = storageService.upload(file, "projects/covers");
        project.setCoverImageUrl(result.url());

        return projectMapper.toResponse(projectRepository.save(project));
    }

    // ─── Public ───────────────────────────────────────────────────────────────

    @Override
    public List<ProjectSummaryResponse> findAllPublished() {
        return projectRepository
                .findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscYearDesc()
                .stream()
                .map(projectMapper::toSummaryResponse)
                .toList();
    }

    @Override
    public ProjectResponse findPublishedById(UUID id) {
        Project project = projectRepository.findByIdAndIsDeletedFalse(id)
                .filter(Project::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", id));
        return projectMapper.toResponse(project);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Project findActiveOrThrow(UUID id) {
        return projectRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", id));
    }

    private void addMemberToProject(Project project, ProjectMemberRequest request) {
        Alumni alumni = alumniRepository.findByIdAndIsDeletedFalse(request.getAlumniId())
                .orElseThrow(() -> new ResourceNotFoundException("Alumni", "id", request.getAlumniId()));

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .alumni(alumni)
                .role(request.getRole())
                .displayOrder(request.getDisplayOrder())
                .build();

        memberRepository.save(member);
    }
}
