package com.modeltech.datamasteryhub.modules.course.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.course.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityPostRepository extends SoftDeleteRepository<CommunityPost, UUID> {

    /** Messages de premier niveau d'une formation, du plus récent au plus ancien. */
    Page<CommunityPost> findAllByBootcampIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(UUID bootcampId, Pageable pageable);

    /** Réponses aux messages donnés, dans l'ordre chronologique. */
    List<CommunityPost> findAllByParentIdInAndIsDeletedFalseOrderByCreatedAtAsc(Collection<UUID> parentIds);

    List<CommunityPost> findAllByParentIdAndIsDeletedFalse(UUID parentId);
}
