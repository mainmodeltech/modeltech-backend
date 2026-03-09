package com.modeltech.datamasteryhub.modules.auth.repository;

import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    Optional<Role> findByNameAndIsDeletedFalse(String name);
}
