package com.modeltech.datamasteryhub.modules.training.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.PaymentStatus;
import com.modeltech.datamasteryhub.modules.training.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegistrationRepository extends SoftDeleteRepository<Registration, UUID> {

    Page<Registration> findAllByStatusAndIsDeletedFalse(RegistrationStatus status, Pageable pageable);

    Page<Registration> findAllByPaymentStatusAndIsDeletedFalse(PaymentStatus paymentStatus, Pageable pageable);

    int countBySession_IdAndStatusAndIsDeletedFalse(UUID sessionId, RegistrationStatus status);

    List<Registration> findAllBySessionIdAndIsDeletedFalse(UUID sessionId);
}
