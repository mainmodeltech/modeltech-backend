package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.LiveReminder;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LiveReminderRepository extends SoftDeleteRepository<LiveReminder, UUID> {

    boolean existsByLessonIdAndSessionIdAndKind(UUID lessonId, UUID sessionId, String kind);
}
