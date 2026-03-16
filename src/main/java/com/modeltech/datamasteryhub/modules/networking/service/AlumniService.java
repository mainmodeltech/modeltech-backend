package com.modeltech.datamasteryhub.modules.networking.service;

import com.modeltech.datamasteryhub.modules.networking.dto.request.CreateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.request.UpdateAlumniRequest;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniResponse;
import com.modeltech.datamasteryhub.modules.networking.dto.response.AlumniSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface AlumniService {

    // Admin
    AlumniResponse   create(CreateAlumniRequest request);
    Page<AlumniResponse> findAllForAdmin(Pageable pageable);
    AlumniResponse   findByIdForAdmin(UUID id);
    AlumniResponse   update(UUID id, UpdateAlumniRequest request);
    AlumniResponse   uploadPhoto(UUID id, MultipartFile file);
    void             softDelete(UUID id);

    // Public
    List<AlumniSummaryResponse> findAllPublished();
}
