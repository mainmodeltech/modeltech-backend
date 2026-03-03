package com.modeltech.datamasteryhub.modules.training.service;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.CreateBootcampSessionRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampRequest;
import com.modeltech.datamasteryhub.modules.training.dto.request.UpdateBootcampSessionRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSessionResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.BootcampSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BootcampService {

    // Public
    List<BootcampResponse> findAllPublished();
    BootcampResponse findPublishedById(UUID id);

    // Admin - bootcamps
    List<BootcampResponse> findAllForAdmin();
    BootcampResponse findByIdForAdmin(UUID id);
    BootcampResponse create(CreateBootcampRequest request);
    BootcampResponse update(UUID id, UpdateBootcampRequest request);
    void delete(UUID id);
    BootcampResponse togglePublished(UUID id);

    // Admin - sessions
    List<BootcampSessionResponse> findSessionsByBootcamp(UUID bootcampId, boolean adminMode);
    BootcampSessionResponse findSessionById(UUID sessionId);
    BootcampSessionResponse createSession(UUID bootcampId, CreateBootcampSessionRequest request);
    BootcampSessionResponse updateSession(UUID sessionId, UpdateBootcampSessionRequest request);
    void deleteSession(UUID sessionId);
    BootcampSessionResponse toggleSessionFeatured(UUID sessionId);

}
