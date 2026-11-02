package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SimCertificationRepository extends JpaRepository<SimCertification, UUID> {

    Optional<SimCertification> findByCodeAndActiveTrue(String code);
}
