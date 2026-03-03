package com.modeltech.datamasteryhub.modules.training.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.training.dto.request.*;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSessionResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.mapper.BootcampMapper;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.service.BootcampService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BootcampServiceImpl implements BootcampService {

    private final BootcampRepository bootcampRepository;
    private final BootcampSessionRepository sessionRepository;
    private final BootcampMapper mapper;

    // ── Public ──────────────────────────────────────────────────────

    @Override
    public List<BootcampResponse> findAllPublished() {
        return bootcampRepository
                .findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponseWithNextSession)
                .collect(Collectors.toList());
    }

    @Override
    public BootcampResponse findPublishedById(UUID id) {
        Bootcamp bootcamp = bootcampRepository.findByIdAndPublishedTrueAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp introuvable : " + id));
        return toResponseWithAllSessions(bootcamp, false);
    }

    // ── Admin - Bootcamps ────────────────────────────────────────────

    @Override
    public List<BootcampResponse> findAllForAdmin() {
        return bootcampRepository
                .findAllByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponseWithNextSession)
                .collect(Collectors.toList());
    }

    @Override
    public BootcampResponse findByIdForAdmin(UUID id) {
        Bootcamp bootcamp = bootcampRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp introuvable : " + id));
        return toResponseWithAllSessions(bootcamp, true);
    }

    @Override
    @Transactional
    public BootcampResponse create(CreateBootcampRequest request) {
        Bootcamp bootcamp = mapper.toEntity(request);
        return mapper.toResponse(bootcampRepository.save(bootcamp));
    }

    @Override
    @Transactional
    public BootcampResponse update(UUID id, UpdateBootcampRequest request) {
        Bootcamp bootcamp = getBootcampOrThrow(id);
        mapper.updateEntity(request, bootcamp);
        return mapper.toResponse(bootcampRepository.save(bootcamp));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Bootcamp bootcamp = getBootcampOrThrow(id);
        bootcamp.setDeleted(true);
        bootcamp.setPublished(false);
        bootcampRepository.save(bootcamp);
    }

    @Override
    @Transactional
    public BootcampResponse togglePublished(UUID id) {
        Bootcamp bootcamp = getBootcampOrThrow(id);
        bootcamp.setPublished(!bootcamp.getPublished());
        return mapper.toResponse(bootcampRepository.save(bootcamp));
    }

    // ── Admin - Sessions ─────────────────────────────────────────────

    @Override
    public List<BootcampSessionResponse> findSessionsByBootcamp(UUID bootcampId, boolean adminMode) {
        getBootcampOrThrow(bootcampId); // vérifie que le bootcamp existe
        List<BootcampSession> sessions = adminMode
                ? sessionRepository.findAllByBootcampIdAndIsDeletedFalseOrderByStartDateAsc(bootcampId)
                : sessionRepository.findAllByBootcampIdAndPublishedTrueAndIsDeletedFalseOrderByStartDateAsc(bootcampId);
        return mapper.toSessionResponseList(sessions);
    }

    @Override
    public BootcampSessionResponse findSessionById(UUID sessionId) {
        return mapper.toSessionResponse(getSessionOrThrow(sessionId));
    }

    @Override
    @Transactional
    public BootcampSessionResponse createSession(UUID bootcampId, CreateBootcampSessionRequest request) {
        Bootcamp bootcamp = getBootcampOrThrow(bootcampId);
        BootcampSession session = mapper.toSessionEntity(request);
        session.setBootcamp(bootcamp);
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public BootcampSessionResponse updateSession(UUID sessionId, UpdateBootcampSessionRequest request) {
        BootcampSession session = getSessionOrThrow(sessionId);
        mapper.updateSessionEntity(request, session);
        // Recalcule isFull si currentParticipants ou max ont changé
        if (session.getMaxParticipants() != null && session.getCurrentParticipants() != null) {
            session.setIsFull(session.getCurrentParticipants() >= session.getMaxParticipants());
        }
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public void deleteSession(UUID sessionId) {
        BootcampSession session = getSessionOrThrow(sessionId);
        session.setDeleted(true);
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public BootcampSessionResponse toggleSessionFeatured(UUID sessionId) {
        BootcampSession session = getSessionOrThrow(sessionId);
        session.setIsFeatured(!session.getIsFeatured());
        return mapper.toSessionResponse(sessionRepository.save(session));
    }

    // ── Privé ────────────────────────────────────────────────────────

    private Bootcamp getBootcampOrThrow(UUID id) {
        return bootcampRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Bootcamp introuvable : " + id));
    }

    private BootcampSession getSessionOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Session introuvable : " + id));
    }

    /**
     * Response avec juste la prochaine session (pour les listings)
     */
    private BootcampResponse toResponseWithNextSession(Bootcamp bootcamp) {
        BootcampResponse response = mapper.toResponse(bootcamp);
        sessionRepository.findFirstByBootcampIdAndIsFeaturedTrueAndPublishedTrueAndIsDeletedFalse(bootcamp.getId())
                .or(() -> sessionRepository.findNextUpcomingSession(bootcamp.getId()))
                .map(mapper::toSessionResponse)
                .ifPresent(response::setNextSession);
        return response;
    }

    /**
     * Response avec toutes les sessions (pour la page détail)
     */
    private BootcampResponse toResponseWithAllSessions(Bootcamp bootcamp, boolean adminMode) {
        BootcampResponse response = mapper.toResponse(bootcamp);
        List<BootcampSession> sessions = adminMode
                ? sessionRepository.findAllByBootcampIdAndIsDeletedFalseOrderByStartDateAsc(bootcamp.getId())
                : sessionRepository.findAllByBootcampIdAndPublishedTrueAndIsDeletedFalseOrderByStartDateAsc(bootcamp.getId());
        response.setSessions(mapper.toSessionResponseList(sessions));
        // Prochaine session mise en avant
        sessions.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsFeatured()))
                .findFirst()
                .or(() -> sessions.stream().findFirst())
                .map(mapper::toSessionResponse)
                .ifPresent(response::setNextSession);
        return response;
    }
}