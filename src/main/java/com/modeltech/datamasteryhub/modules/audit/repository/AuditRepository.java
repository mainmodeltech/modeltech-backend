package com.modeltech.datamasteryhub.modules.audit.repository;

import com.modeltech.datamasteryhub.modules.audit.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID>, JpaSpecificationExecutor<AuditEntry> {
}
