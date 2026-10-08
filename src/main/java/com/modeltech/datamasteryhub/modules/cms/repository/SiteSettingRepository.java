package com.modeltech.datamasteryhub.modules.cms.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.cms.entity.SiteSetting;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteSettingRepository extends SoftDeleteRepository<SiteSetting, UUID> {

    List<SiteSetting> findAllByIsDeletedFalseOrderByKeyAsc();

    /** Inclut les clés supprimées : on les « ressuscite » plutôt que de violer l'unicité. */
    Optional<SiteSetting> findByKey(String key);
}
