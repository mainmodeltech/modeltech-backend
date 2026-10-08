package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizChoiceRepository extends SoftDeleteRepository<QuizChoice, UUID> {

    @Query("""
            SELECT c FROM QuizChoice c
            WHERE c.question.id IN :questionIds AND c.isDeleted = false
            ORDER BY c.position
            """)
    List<QuizChoice> findAllByQuestionIds(@Param("questionIds") Collection<UUID> questionIds);
}
