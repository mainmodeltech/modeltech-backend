package com.modeltech.datamasteryhub.modules.training.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Resume financier d'une session / cohorte.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SessionFinancialSummaryResponse {

    private UUID sessionId;
    private String sessionName;

    /** Nombre total d'inscriptions (hors annulees) */
    private int totalRegistrations;

    /** Objectif financier : somme des montants dus */
    private int revenueTarget;

    /** Total encaisse : somme de tous les paiements */
    private int totalCollected;

    /** Reste a recouvrer */
    private int remainingToCollect;

    /** Taux de recouvrement (0-100) */
    private double collectionRate;

    /** Nombre d'inscriptions PAID */
    private int paidCount;

    /** Nombre d'inscriptions PARTIAL */
    private int partialCount;

    /** Nombre d'inscriptions UNPAID */
    private int unpaidCount;
}
