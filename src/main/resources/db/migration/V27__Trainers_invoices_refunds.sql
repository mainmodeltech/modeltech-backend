-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "trainers-invoices" : formateur de session, inscription manuelle, annulation / remboursement,
-- factures PDF pour les entreprises. Additif.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Formateur d'une session (compte du back-office avec le rôle TRAINER, vérifié par le service)
ALTER TABLE bootcamp_sessions ADD COLUMN IF NOT EXISTS trainer_id UUID REFERENCES admin_users(id);
CREATE INDEX IF NOT EXISTS idx_bootcamp_sessions_trainer ON bootcamp_sessions (trainer_id) WHERE trainer_id IS NOT NULL;

-- 2. Origine d'une inscription et annulation
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS source VARCHAR(10) NOT NULL DEFAULT 'WEBSITE';
ALTER TABLE registrations ADD CONSTRAINT registrations_source_check CHECK (source IN ('WEBSITE', 'ADMIN'));
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS cancelled_at     TIMESTAMP;
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS cancelled_reason TEXT;

-- 3. Remboursement d'une échéance (le remboursement lui-même se fait hors plateforme : on le consigne)
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'DECLARED', 'CONFIRMED', 'CANCELLED', 'REFUNDED'));
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_at   TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_by   VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_reason TEXT;

-- 4. Factures : numérotation continue par année (un compteur verrouillé, pas une séquence qui laisse des trous)
CREATE TABLE invoice_counters (
    year        INTEGER PRIMARY KEY,
    last_number INTEGER NOT NULL
);

CREATE TABLE invoices (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    number           VARCHAR(30)  NOT NULL,
    registration_id  UUID         NOT NULL REFERENCES registrations(id),
    issue_date       DATE         NOT NULL,
    due_date         DATE,
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('ISSUED', 'CANCELLED')),
    buyer_name       VARCHAR(255) NOT NULL,
    buyer_contact    VARCHAR(255),
    buyer_email      VARCHAR(255),
    buyer_address    VARCHAR(500),
    purchase_order_ref VARCHAR(255),
    description      VARCHAR(500) NOT NULL,
    unit_amount      BIGINT       NOT NULL CHECK (unit_amount >= 0),
    discount_label   VARCHAR(255),
    discount_amount  BIGINT       NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    vat_percent      NUMERIC(5,2) NOT NULL DEFAULT 0,
    total_excl_vat   BIGINT       NOT NULL,
    vat_amount       BIGINT       NOT NULL DEFAULT 0,
    total            BIGINT       NOT NULL CHECK (total >= 0),
    currency         VARCHAR(3)   NOT NULL DEFAULT 'XOF',
    installments     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    seller           JSONB        NOT NULL,
    notes            TEXT,
    cancelled_at     TIMESTAMP,
    cancelled_reason TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_invoices_number UNIQUE (number)
);

-- Une seule facture en vigueur par inscription (une facture annulée peut être refaite)
CREATE UNIQUE INDEX uq_invoices_active ON invoices (registration_id) WHERE status = 'ISSUED' AND is_deleted = FALSE;
