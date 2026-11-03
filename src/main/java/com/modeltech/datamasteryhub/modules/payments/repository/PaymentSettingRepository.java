package com.modeltech.datamasteryhub.modules.payments.repository;

import com.modeltech.datamasteryhub.modules.payments.entity.PaymentSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSettingRepository extends JpaRepository<PaymentSetting, Short> {
}
