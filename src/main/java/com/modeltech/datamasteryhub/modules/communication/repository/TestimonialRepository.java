package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.modules.communication.entity.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface TestimonialRepository extends JpaRepository<Testimonial, UUID> {
    List<Testimonial> findAllByOrderByCreatedAtDesc();
    Optional<Testimonial> findByIdAndIsDeletedFalse(UUID id);
}
