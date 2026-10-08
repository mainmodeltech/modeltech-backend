package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.SessionMessage;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionMessageRepository extends SoftDeleteRepository<SessionMessage, UUID> {

    List<SessionMessage> findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID sessionId);
}
