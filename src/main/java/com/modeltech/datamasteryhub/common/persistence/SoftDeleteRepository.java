package com.modeltech.datamasteryhub.common.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface SoftDeleteRepository<T extends BaseEntity, ID> extends JpaRepository<T, ID> {

    List<T> findAllByIsDeletedFalse();

    Page<T> findAllByIsDeletedFalse(Pageable pageable);

    Optional<T> findByIdAndIsDeletedFalse(ID id);
}
