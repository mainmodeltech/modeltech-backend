package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprintSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SimBlueprintSkillRepository extends JpaRepository<SimBlueprintSkill, UUID> {

    Optional<SimBlueprintSkill> findByCodeAndDomainBlueprintId(String code, UUID blueprintId);
}
