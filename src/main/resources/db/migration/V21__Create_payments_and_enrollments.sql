-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "enrollment-payment" : montants numériques, paiements (déclaration manuelle +
-- confirmation admin), accès apprenant (enrollments), nouveaux statuts d'inscription.
-- Additif uniquement : les prix « texte » d'affichage (price, price_override,
-- early_bird_price) restent en place et ne sont jamais convertis automatiquement.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Montants numériques (XOF entier). NULL tant que l'équipe ne les a pas saisis :
--    on ne devine pas un montant à partir d'une chaîne comme « 150 000 FCFA ».
ALTER TABLE bootcamps         ADD COLUMN IF NOT EXISTS price_amount          BIGINT;
ALTER TABLE bootcamps         ADD COLUMN IF NOT EXISTS currency              VARCHAR(3) NOT NULL DEFAULT 'XOF';
ALTER TABLE bootcamp_sessions ADD COLUMN IF NOT EXISTS price_override_amount BIGINT;
ALTER TABLE bootcamp_sessions ADD COLUMN IF NOT EXISTS early_bird_amount     BIGINT;

ALTER TABLE bootcamps         ADD CONSTRAINT bootcamps_price_amount_check
    CHECK (price_amount IS NULL OR price_amount >= 0);
ALTER TABLE bootcamp_sessions ADD CONSTRAINT sessions_amounts_check
    CHECK ((price_override_amount IS NULL OR price_override_amount >= 0)
       AND (early_bird_amount     IS NULL OR early_bird_amount     >= 0));

-- 2. Inscriptions : 3 nouveaux statuts + suivi de l'acceptation
ALTER TABLE registrations DROP CONSTRAINT IF EXISTS registrations_status_check;
ALTER TABLE registrations
    ADD CONSTRAINT registrations_status_check
        CHECK (status IN ('PENDING', 'PAYMENT_PENDING', 'PAYMENT_TO_CONFIRM',
                          'CONFIRMED', 'COMPLETED', 'CANCELLED', 'REJECTED'));

ALTER TABLE registrations ADD COLUMN IF NOT EXISTS accepted_at     TIMESTAMP;
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS accepted_by     VARCHAR(255);
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS rejected_reason TEXT;
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS payer_type      VARCHAR(20);
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS total_amount    BIGINT;
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS learner_id      UUID REFERENCES learners(id);

ALTER TABLE registrations ADD CONSTRAINT registrations_payer_type_check
    CHECK (payer_type IS NULL OR payer_type IN ('INDIVIDUAL', 'COMPANY'));

CREATE INDEX IF NOT EXISTS idx_registrations_learner ON registrations (learner_id);

-- 3. Paiements : une ligne par échéance (1 à N par inscription)
CREATE TABLE payments (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    registration_id   UUID         NOT NULL REFERENCES registrations(id),
    amount            BIGINT       NOT NULL CHECK (amount >= 0),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'XOF',
    installment_number INTEGER     NOT NULL DEFAULT 1 CHECK (installment_number >= 1),
    installment_count  INTEGER     NOT NULL DEFAULT 1 CHECK (installment_count >= 1),
    due_date          DATE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'DECLARED', 'CONFIRMED', 'CANCELLED')),
    method            VARCHAR(20)
                      CHECK (method IS NULL OR method IN ('WAVE', 'ORANGE_MONEY', 'VIREMENT', 'ENTREPRISE', 'ESPECES')),
    reference         VARCHAR(255),
    declared_at       TIMESTAMP,
    paid_at           TIMESTAMP,
    confirmed_at      TIMESTAMP,
    confirmed_by      VARCHAR(255),
    rejection_reason  TEXT,
    proof_object_key  VARCHAR(512),
    proof_url         VARCHAR(1024),
    public_token      VARCHAR(255) NOT NULL,
    token_expires_at  TIMESTAMP    NOT NULL,
    reminder_count    INTEGER      NOT NULL DEFAULT 0,
    last_reminder_at  TIMESTAMP,
    invoice_ref       VARCHAR(255),
    purchase_order_ref VARCHAR(255),
    notes             TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(255),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(255),
    CONSTRAINT uq_payments_public_token UNIQUE (public_token),
    CONSTRAINT uq_payments_installment UNIQUE (registration_id, installment_number),
    CONSTRAINT payments_installment_range CHECK (installment_number <= installment_count)
);

CREATE INDEX idx_payments_registration ON payments (registration_id);
CREATE INDEX idx_payments_status       ON payments (status) WHERE is_deleted = FALSE;

-- 4. Accès apprenant : un compte apprenant ↔ une session, issu d'une inscription payée
CREATE TABLE enrollments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    learner_id       UUID        NOT NULL REFERENCES learners(id),
    session_id       UUID        REFERENCES bootcamp_sessions(id),
    registration_id  UUID        NOT NULL REFERENCES registrations(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'SUSPENDED', 'COMPLETED', 'CANCELLED')),
    access_starts_at DATE,
    access_ends_at   DATE,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_at       TIMESTAMP,
    updated_by       VARCHAR(255),
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(255),
    CONSTRAINT uq_enrollments_registration UNIQUE (registration_id)
);

CREATE INDEX idx_enrollments_learner ON enrollments (learner_id);
CREATE INDEX idx_enrollments_session ON enrollments (session_id);
