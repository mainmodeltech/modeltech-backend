-- ============================================================================
-- V17 : Module paiements
--   1. Ajouter payment_status, amount_due, amount_paid sur registrations
--   2. Creer la table payments
-- ============================================================================

-- ── 1. Nouveaux champs sur registrations ─────────────────────────────────────

ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) NOT NULL DEFAULT 'UNPAID'
        CONSTRAINT registrations_payment_status_check
            CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID'));

ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS amount_due INTEGER;

ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS amount_paid INTEGER NOT NULL DEFAULT 0;

-- ── 2. Table payments ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_id   UUID NOT NULL REFERENCES registrations(id),
    amount            INTEGER NOT NULL,
    payment_date      DATE NOT NULL,
    payment_method    VARCHAR(50) NOT NULL
        CONSTRAINT payments_method_check
            CHECK (payment_method IN ('WAVE', 'ORANGE_MONEY', 'VIREMENT', 'CASH', 'CARTE')),
    reference         VARCHAR(255),
    notes             TEXT,
    recorded_by       VARCHAR(255),

    -- BaseEntity audit columns
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN NOT NULL DEFAULT false,
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(255)
);

-- Index pour les lookups par inscription
CREATE INDEX IF NOT EXISTS idx_payments_registration_id
    ON payments(registration_id)
    WHERE is_deleted = false;
