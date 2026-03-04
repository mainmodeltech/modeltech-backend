package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import com.modeltech.datamasteryhub.modules.training.mapper.RegistrationMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.RegistrationRepository;
import com.modeltech.datamasteryhub.modules.training.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final BootcampRepository bootcampRepository;
    private final RegistrationMapper registrationMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RegistrationResponse register(CreateRegistrationRequest request) {
        Registration registration = registrationMapper.toEntity(request);
        registration.setStatus(RegistrationStatus.PENDING);

        // Si bootcampId fourni, on associe le bootcamp et on recupere le titre
        if (request.getBootcampId() != null) {
            Bootcamp bootcamp = bootcampRepository.findById(request.getBootcampId())
                    .orElse(null);
            if (bootcamp != null) {
                registration.setBootcamp(bootcamp);
                registration.setBootcampTitle(bootcamp.getTitle());
            }
        }

        // Si pas de bootcampTitle auto-rempli mais fourni dans la request
        if (registration.getBootcampTitle() == null && request.getBootcampTitle() != null) {
            registration.setBootcampTitle(request.getBootcampTitle());
        }

        Registration saved = registrationRepository.save(registration);

        log.info("Nouvelle inscription creee : {} {} pour {}",
                saved.getFirstName(), saved.getLastName(), saved.getBootcampTitle());

        // Notifications asynchrones (Slack + Email)
        notificationService.notifyNewRegistration(saved);

        return registrationMapper.toResponse(saved);
    }

    @Override
    public Page<RegistrationResponse> findAllForAdmin(Pageable pageable, RegistrationStatus status) {
        Page<Registration> page;
        if (status != null) {
            page = registrationRepository.findAllByStatusAndIsDeletedFalse(status, pageable);
        } else {
            page = registrationRepository.findAllByIsDeletedFalse(pageable);
        }
        return page.map(registrationMapper::toResponse);
    }

    @Override
    public RegistrationResponse findByIdForAdmin(UUID id) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
        return registrationMapper.toResponse(registration);
    }

    @Override
    @Transactional
    public RegistrationResponse updateStatus(UUID id, RegistrationStatus status) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
        registration.setStatus(status);
        return registrationMapper.toResponse(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Registration registration = registrationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", id));
        registration.setDeleted(true);
        registration.setDeletedAt(LocalDateTime.now());
        registrationRepository.save(registration);
    }
}
