package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimAttemptEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimAttemptEventRepository extends JpaRepository<SimAttemptEvent, UUID> {
}
