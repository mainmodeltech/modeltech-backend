package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimPlanRepository extends JpaRepository<SimPlan, PlanCode> {

    List<SimPlan> findAllByOrderByDisplayOrderAsc();

    List<SimPlan> findAllByActiveTrueOrderByDisplayOrderAsc();
}
