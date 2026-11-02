package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SimBlueprintRepository extends JpaRepository<SimBlueprint, UUID> {

    /** Le blueprint en vigueur : la version active la plus récente du guide de la certification. */
    Optional<SimBlueprint> findFirstByCertificationCodeAndActiveTrueOrderByVersionDateDesc(String certificationCode);
}
