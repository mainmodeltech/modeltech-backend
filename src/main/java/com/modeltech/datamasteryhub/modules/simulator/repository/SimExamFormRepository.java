package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimExamForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimExamFormRepository extends JpaRepository<SimExamForm, UUID> {
}
