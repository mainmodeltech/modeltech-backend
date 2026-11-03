package com.modeltech.datamasteryhub.modules.payments.repository;

import com.modeltech.datamasteryhub.modules.payments.PayableService;
import com.modeltech.datamasteryhub.modules.payments.entity.PaymentServiceSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentServiceSettingRepository extends JpaRepository<PaymentServiceSetting, PayableService> {
}
