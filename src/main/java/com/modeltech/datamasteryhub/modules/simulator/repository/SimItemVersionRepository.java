package com.modeltech.datamasteryhub.modules.simulator.repository;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimItemVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimItemVersionRepository extends JpaRepository<SimItemVersion, UUID>, JpaSpecificationExecutor<SimItemVersion> {

    /** Versions publiées d'une compétence, avec leur question, leur compétence et leur domaine (assemblage d'un examen). */
    @Query("SELECT v FROM SimItemVersion v JOIN FETCH v.item i JOIN FETCH i.skill s JOIN FETCH s.domain WHERE s.id = :skillId "
            + "AND v.status = com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus.PUBLISHED")
    List<SimItemVersion> findPublishedBySkillId(@Param("skillId") UUID skillId);

    /** Versions publiées des questions échantillon de l'essai gratuit. */
    @Query("SELECT v FROM SimItemVersion v JOIN FETCH v.item i JOIN FETCH i.skill s JOIN FETCH s.domain WHERE i.trialSample = true "
            + "AND v.status = com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus.PUBLISHED")
    List<SimItemVersion> findPublishedTrialSamples();

    Optional<SimItemVersion> findByItemIdAndVersionNo(UUID itemId, int versionNo);

    Optional<SimItemVersion> findByItemIdAndStatus(UUID itemId, ItemStatus status);

    @Query("SELECT v.versionNo FROM SimItemVersion v WHERE v.item.id = :itemId ORDER BY v.versionNo")
    List<Integer> versionNumbers(@Param("itemId") UUID itemId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM SimItemVersion v WHERE v.item.id = :itemId")
    int maxVersionNo(@Param("itemId") UUID itemId);

    /** Une version non figée existe-t-elle déjà ? (on n'ouvre pas deux brouillons en parallèle pour la même question) */
    boolean existsByItemIdAndStatusIn(UUID itemId, List<ItemStatus> statuses);

    /** Couverture : nombre de versions par sous-compétence et par statut, hors versions retirées. */
    @Query("SELECT v.item.skill.id, v.status, COUNT(v) FROM SimItemVersion v WHERE v.status <> com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus.RETIRED GROUP BY v.item.skill.id, v.status")
    List<Object[]> countBySkillAndStatus();
}
