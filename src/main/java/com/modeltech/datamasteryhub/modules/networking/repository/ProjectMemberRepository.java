package com.modeltech.datamasteryhub.modules.networking.repository;

import com.modeltech.datamasteryhub.modules.networking.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember>    findAllByProjectId(UUID projectId);
    Optional<ProjectMember> findByProjectIdAndAlumniId(UUID projectId, UUID alumniId);
    boolean                existsByProjectIdAndAlumniId(UUID projectId, UUID alumniId);
    long                   countByProjectId(UUID projectId);
}
