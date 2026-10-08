package com.modeltech.datamasteryhub.modules.stats.service;

import com.modeltech.datamasteryhub.modules.stats.dto.StatsPayloads;

import java.time.LocalDate;

public interface StatsService {

    /** Compteurs « à traiter » (instantanés, sans période). */
    StatsPayloads.Actions actions();

    /**
     * Indicateurs sur une période (bornes incluses ; 30 derniers jours par défaut, 366 jours maximum).
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 si la période est incohérente
     */
    StatsPayloads.Overview overview(LocalDate from, LocalDate to);
}
