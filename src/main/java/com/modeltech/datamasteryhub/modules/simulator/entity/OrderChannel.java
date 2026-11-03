package com.modeltech.datamasteryhub.modules.simulator.entity;

/** Comment une commande est payée : en ligne (passerelle, confirmation automatique) ou manuellement (déclaration + justificatif, confirmée par l'équipe). */
public enum OrderChannel {
    ONLINE, MANUAL
}
