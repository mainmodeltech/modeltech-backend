package com.modeltech.datamasteryhub.modules.communication.controller;

import com.modeltech.datamasteryhub.modules.communication.dto.request.TestimonialRequestDTO;
import com.modeltech.datamasteryhub.modules.communication.dto.response.TestimonialResponseDTO;
import com.modeltech.datamasteryhub.modules.communication.service.TestimonialService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/testimonials")
@RequiredArgsConstructor
@Slf4j
public class TestimonialController {

    private final TestimonialService testimonialService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestimonialResponseDTO create(@Valid @RequestBody TestimonialRequestDTO dto) {
        return testimonialService.saveTestimonial(dto);
    }

     @GetMapping
     @Operation(summary = "Lister tous les témoignages (hors supprimés)")
    public List<TestimonialResponseDTO> list() {
        List<TestimonialResponseDTO> testimonials = testimonialService.getAllTestimonials();
        log.info("Récupération de {} témoiganges", testimonials);
        return testimonials;
    }
}
