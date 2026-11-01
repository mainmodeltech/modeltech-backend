package com.modeltech.datamasteryhub.modules.simulator.strategy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Question invalide (structure, règles du type, champs manquants) : renvoyée en 400 avec un message lisible. */
public class InvalidItemException extends ResponseStatusException {

    public InvalidItemException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
