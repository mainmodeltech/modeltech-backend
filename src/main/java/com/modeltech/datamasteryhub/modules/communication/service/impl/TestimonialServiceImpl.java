package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.Testimonial;
import com.modeltech.datamasteryhub.modules.communication.mapper.TestimonialMapper;
import com.modeltech.datamasteryhub.modules.communication.repository.TestimonialRepository;
import com.modeltech.datamasteryhub.modules.communication.service.TestimonialService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestimonialServiceImpl implements TestimonialService {

    private final TestimonialRepository testimonialRepository;
    private final TestimonialMapper testimonialMapper;

    @Override
    @Transactional
    public TestimonialResponseDTO saveTestimonial(TestimonialRequestDTO dto) {
        Testimonial message = testimonialMapper.toEntity(dto);

        return testimonialMapper.toResponseDto(testimonialRepository.save(message));
    }

    @Override
    public List<TestimonialResponseDTO> getAllTestimonials() {
        return testimonialRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(testimonialMapper::toResponseDto)
                .collect(Collectors.toList());
    }
}
