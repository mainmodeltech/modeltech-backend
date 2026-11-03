package com.modeltech.datamasteryhub.modules.training.enums;

public enum CheckoutStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
