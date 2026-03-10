package com.modeltech.datamasteryhub.modules.communication.mapper;

import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.entity.Testimonial;
import org.springframework.stereotype.Component;

@Component
public class TestimonialMapper {

    public TestimonialResponseDTO toResponse(Testimonial testimonial) {
        TestimonialResponseDTO response = new TestimonialResponseDTO();
        response.setId(testimonial.getId());
        response.setName(testimonial.getName());
        response.setContent(testimonial.getContent());
        response.setCompany(testimonial.getCompany());
        response.setRole(testimonial.getRole());
        response.setBootcamp(testimonial.getBootcamp());
        response.setResult(testimonial.getResult());
        response.setRating(testimonial.getRating());
        response.setPublished(testimonial.getPublished());
        response.setDisplayOrder(testimonial.getDisplayOrder());
        response.setCreatedAt(testimonial.getCreatedAt());
        response.setUpdatedAt(testimonial.getUpdatedAt());
        return response;
    }

    public Testimonial toEntity(TestimonialRequestDTO request) {
        Testimonial testimonial = new Testimonial();
        applyRequest(testimonial, request);
        return testimonial;
    }

    public void updateEntity(Testimonial testimonial, TestimonialRequestDTO request) {
        applyRequest(testimonial, request);
    }

    // -----------------------------------------------------------------------
    private void applyRequest(Testimonial t, TestimonialRequestDTO req) {
        t.setName(req.getName());
        t.setContent(req.getContent());
        t.setCompany(req.getCompany());
        t.setRole(req.getRole());
        t.setBootcamp(req.getBootcamp());
        t.setResult(req.getResult());
        t.setRating(req.getRating() != null ? req.getRating() : 5);
        t.setPublished(req.getPublished() != null ? req.getPublished() : true);
        t.setDisplayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0);
    }
}