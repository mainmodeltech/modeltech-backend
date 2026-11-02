package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimAttemptAnswerRepository extends JpaRepository<SimAttemptAnswer, UUID> {

    List<SimAttemptAnswer> findAllByAttemptId(UUID attemptId);

    Optional<SimAttemptAnswer> findByAttemptIdAndFormItemId(UUID attemptId, UUID formItemId);
}
