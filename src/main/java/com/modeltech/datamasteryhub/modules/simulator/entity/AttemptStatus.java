package com.modeltech.datamasteryhub.modules.simulator.entity;

/** IN_PROGRESS → SUBMITTED (remise par l'apprenant) ou EXPIRED (temps écoulé, remise automatique) ; les deux sont notés. */
public enum AttemptStatus {
    IN_PROGRESS,
    SUBMITTED,
    EXPIRED;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
