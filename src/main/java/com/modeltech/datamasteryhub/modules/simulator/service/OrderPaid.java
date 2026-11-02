package com.modeltech.datamasteryhub.modules.simulator.service;

import java.util.UUID;

/** Événement de domaine (Observer) : une commande vient d'être confirmée payée et son accès ouvert. Publié dans la transaction, traité après sa validation. */
public record OrderPaid(UUID orderId) {}
