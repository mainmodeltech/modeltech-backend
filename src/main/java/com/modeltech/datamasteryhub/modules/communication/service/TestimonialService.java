package com.modeltech.datamasteryhub.modules.communication.service;

import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import java.util.List;
import java.util.UUID;

public interface TestimonialService {
    TestimonialResponseDTO saveTestimonial(TestimonialRequestDTO dto);
    List<TestimonialResponseDTO> getAllTestimonials();
}
