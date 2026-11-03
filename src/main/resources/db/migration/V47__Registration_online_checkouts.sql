-- Paiement en ligne des inscriptions (lot L2) : une « tentative de paiement » couvre une échéance ou tout le solde restant.
-- Le montant encaissé (prix + éventuels frais) est figé ici ; les échéances (payments.amount) gardent le prix du catalogue.

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_method_check
    CHECK (method IS NULL OR method IN ('WAVE', 'ORANGE_MONEY', 'VIREMENT', 'ENTREPRISE', 'ESPECES', 'PAYDUNYA'));

CREATE TABLE payment_checkout (
    id               UUID         PRIMARY KEY,
    public_ref       UUID         NOT NULL UNIQUE,
    registration_id  UUID         NOT NULL REFERENCES registrations (id),
    scope            VARCHAR(12)  NOT NULL CHECK (scope IN ('INSTALLMENT', 'BALANCE')),
    amount_xof       BIGINT       NOT NULL CHECK (amount_xof > 0),
    fee_xof          BIGINT       NOT NULL DEFAULT 0 CHECK (fee_xof >= 0),
    charged_xof      BIGINT       NOT NULL CHECK (charged_xof >= amount_xof),
    status           VARCHAR(12)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED')),
    provider         VARCHAR(20)  NOT NULL,
    provider_mode    VARCHAR(10),
    provider_token   VARCHAR(255) UNIQUE,
    checkout_url     VARCHAR(1024),
    receipt_url      VARCHAR(1024),
    failure_reason   TEXT,
    last_checked_at  TIMESTAMP,
    paid_at          TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

CREATE INDEX idx_payment_checkout_registration ON payment_checkout (registration_id);
CREATE INDEX idx_payment_checkout_pending ON payment_checkout (status, created_at);

-- Échéances couvertes par une tentative (une pour « INSTALLMENT », toutes les échéances ouvertes pour « BALANCE »)
CREATE TABLE payment_checkout_item (
    checkout_id UUID NOT NULL REFERENCES payment_checkout (id) ON DELETE CASCADE,
    payment_id  UUID NOT NULL REFERENCES payments (id),
    PRIMARY KEY (checkout_id, payment_id)
);

CREATE INDEX idx_payment_checkout_item_payment ON payment_checkout_item (payment_id);
