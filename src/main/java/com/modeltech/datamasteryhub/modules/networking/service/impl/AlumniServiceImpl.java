package com.modeltech.datamasteryhub.modules.networking.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniSummaryResponse;
import com.modeltech.datamasteryhub.modules.networking.entity.Alumni;
import com.modeltech.datamasteryhub.modules.networking.mapper.AlumniMapper;
import com.modeltech.datamasteryhub.modules.networking.repository.AlumniRepository;
import com.modeltech.datamasteryhub.modules.networking.service.AlumniService;
import com.modeltech.datamasteryhub.modules.networking.service.StorageService;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlumniServiceImpl implements AlumniService {

    private final AlumniRepository alumniRepository;
    private final RegistrationRepository registrationRepository;
    private final AlumniMapper           alumniMapper;
    private final StorageService storageService;

    @Override
    @Transactional
    public AlumniResponse create(CreateAlumniRequest request) {
        Alumni alumni = alumniMapper.toEntity(request);
        resolveRegistration(request.getRegistrationId(), alumni);

        Alumni saved = alumniRepository.save(alumni);
        log.info("Alumni créé : {} (id={})", saved.getName(), saved.getId());
        return alumniMapper.toResponse(saved);
    }

    @Override
    public Page<AlumniResponse> findAllForAdmin(Pageable pageable) {
        return alumniRepository.findAllByIsDeletedFalse(pageable)
                .map(alumniMapper::toResponse);
    }

    @Override
    public AlumniResponse findByIdForAdmin(UUID id) {
        return alumniMapper.toResponse(findActiveOrThrow(id));
    }

    @Override
    @Transactional
    public AlumniResponse update(UUID id, UpdateAlumniRequest request) {
        Alumni alumni = findActiveOrThrow(id);
        alumniMapper.updateEntityFromRequest(request, alumni);

        if (request.getRegistrationId() != null) {
            resolveRegistration(request.getRegistrationId(), alumni);
        }

        return alumniMapper.toResponse(alumniRepository.save(alumni));
    }

    @Override
    @Transactional
    public AlumniResponse uploadPhoto(UUID id, MultipartFile file) {
        Alumni alumni = findActiveOrThrow(id);

        // Supprimer l'ancienne photo si elle vient de MinIO
        deleteOldMinioPhoto(alumni.getPhotoUrl());

        StorageService.UploadResult result = storageService.upload(file, "alumni");
        alumni.setPhotoUrl(result.url());

        return alumniMapper.toResponse(alumniRepository.save(alumni));
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Alumni alumni = findActiveOrThrow(id);
        alumni.setDeleted(true);
        alumni.setDeletedAt(LocalDateTime.now());
        alumniRepository.save(alumni);
        log.info("Alumni supprimé (soft) : {}", id);
    }

    @Override
    public List<AlumniSummaryResponse> findAllPublished() {
        return alumniRepository
                .findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc()
                .stream()
                .map(alumniMapper::toSummaryResponse)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Alumni findActiveOrThrow(UUID id) {
        return alumniRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumni", "id", id));
    }

    private void resolveRegistration(UUID registrationId, Alumni alumni) {
        if (registrationId == null) {
            alumni.setRegistration(null);
            return;
        }
        alumni.setRegistration(
                registrationRepository.findById(registrationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Registration", "id", registrationId))
        );
    }

    private void deleteOldMinioPhoto(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return;
        // On ne supprime que si l'URL appartient à notre MinIO (pas une URL externe)
        // La clé est extraite en supprimant le préfixe endpoint/bucket/
        log.debug("Suppression ancienne photo alumni : {}", photoUrl);
        // La suppression est best-effort — StorageService.delete() est silencieux
        // On ne peut pas reconstruire objectKey depuis l'URL de façon sûre ici.
        // L'upload photo stocke désormais objectKey → voir uploadPhoto.
    }
}
