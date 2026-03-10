package com.modeltech.datamasteryhub.modules.communication.service.impl;

import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.Testimonial;
import com.modeltech.datamasteryhub.modules.communication.mapper.TestimonialMapper;
import com.modeltech.datamasteryhub.modules.communication.repository.TestimonialRepository;
import com.modeltech.datamasteryhub.modules.communication.service.TestimonialService;
import jakarta.persistence.EntityNotFoundException;
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

    // ── Public ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TestimonialResponseDTO> getPublished() {
        return testimonialRepository
                .findByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(testimonialMapper::toResponse)
                .collect(Collectors.toList());
    }

    // ── Admin ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TestimonialResponseDTO> getAll() {
        return testimonialRepository
                .findByIsDeletedFalseOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(testimonialMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TestimonialResponseDTO getById(UUID id) {
        return testimonialMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public TestimonialResponseDTO create(TestimonialRequestDTO request) {
        Testimonial testimonial = testimonialMapper.toEntity(request);
        return testimonialMapper.toResponse(testimonialRepository.save(testimonial));
    }

    @Override
    @Transactional
    public TestimonialResponseDTO update(UUID id, TestimonialRequestDTO request) {
        Testimonial testimonial = findOrThrow(id);
        testimonialMapper.updateEntity(testimonial, request);
        return testimonialMapper.toResponse(testimonialRepository.save(testimonial));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Testimonial testimonial = findOrThrow(id);
        testimonial.setDeleted(true);
        testimonialRepository.save(testimonial);
    }

    @Override
    @Transactional
    public TestimonialResponseDTO togglePublished(UUID id) {
        Testimonial testimonial = findOrThrow(id);
        testimonial.setPublished(!testimonial.getPublished());
        return testimonialMapper.toResponse(testimonialRepository.save(testimonial));
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private Testimonial findOrThrow(UUID id) {
        return testimonialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Témoignage introuvable : " + id));
    }
}
