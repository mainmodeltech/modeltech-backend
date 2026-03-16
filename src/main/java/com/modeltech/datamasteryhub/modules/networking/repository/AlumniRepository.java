package com.modeltech.datamasteryhub.modules.networking.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.networking.entity.Alumni;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlumniRepository   extends SoftDeleteRepository<Alumni, UUID> {

   Optional<Alumni> findByIdAndIsDeletedFalse(UUID id);
   List<Alumni> findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc();
}
