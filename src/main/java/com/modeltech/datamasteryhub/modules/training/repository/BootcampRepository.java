package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BootcampRepository extends SoftDeleteRepository<Bootcamp, UUID> {

    Page<Bootcamp> findAllByPublishedTrueAndIsDeletedFalse(Pageable pageable);

    Optional<Bootcamp> findByIdAndPublishedTrueAndIsDeletedFalse(UUID id);
}
