package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.SimItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SimItemRepository extends JpaRepository<SimItem, UUID> {

    Optional<SimItem> findByPublicCode(String publicCode);

    /** Plus grand numéro de séquence déjà attribué pour un préfixe de code (ex. « PL300-MOD- »). */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(public_code FROM '[0-9]+$') AS INTEGER)), 0) FROM sim_item WHERE public_code LIKE :prefix || '%'",
            nativeQuery = true)
    int maxSequence(@Param("prefix") String prefix);
}
