package com.modeltech.datamasteryhub.modules.payments;

/**
 * Ce qui est réellement proposé à un client pour un service, à l'instant T.
 *
 * @param online   le paiement en ligne est proposé
 * @param manual   le paiement manuel (Wave, Orange Money, virement + justificatif) est proposé : au moins l'un des deux est toujours vrai
 * @param fallback le manuel n'est proposé que parce que le paiement en ligne, voulu pour ce service, est momentanément indisponible
 * @param reason   motif lisible du repli (affiché au client), {@code null} sinon
 */
public record PaymentOptions(boolean online, boolean manual, boolean fallback, String reason) {}
