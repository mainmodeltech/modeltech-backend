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
public interface LiveAttendanceRepository extends SoftDeleteRepository<LiveAttendance, UUID> {

    List<LiveAttendance> findAllByRollCallIdIn(Collection<UUID> rollCallIds);

    List<LiveAttendance> findAllByRollCallId(UUID rollCallId);
}
