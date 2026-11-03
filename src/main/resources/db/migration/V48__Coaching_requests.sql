-- Coaching (lot L3) : demande publique → analyse par le responsable pédagogique → formateur → validation et lien de paiement → paiement.
-- Le montant et l'offre sont fixés à la validation, par une personne de l'équipe (jamais par le visiteur).

CREATE TABLE coaching_request (
    id                UUID          PRIMARY KEY,
    -- Secret du lien de paiement (256 bits), créé à la validation
    pay_token         VARCHAR(64)   UNIQUE,
    token_expires_at  TIMESTAMP,

    first_name        VARCHAR(100)  NOT NULL,
    last_name         VARCHAR(100),
    email             VARCHAR(255)  NOT NULL,
    phone             VARCHAR(50),
    company           VARCHAR(255),
    tier              VARCHAR(160),
    slot              VARCHAR(200),
    message           TEXT,
    terms_accepted_at TIMESTAMP     NOT NULL,

    status            VARCHAR(20)   NOT NULL DEFAULT 'NEW'
                      CHECK (status IN ('NEW', 'IN_REVIEW', 'AWAITING_PAYMENT', 'PAYMENT_DECLARED', 'PAID', 'DECLINED', 'CANCELLED', 'EXPIRED')),
    assigned_to       VARCHAR(255),
    trainer_email     VARCHAR(255),
    trainer_name      VARCHAR(255),
    internal_notes    TEXT,
    closing_reason    TEXT,

    offer_label       VARCHAR(255),
    amount_xof        BIGINT        CHECK (amount_xof IS NULL OR amount_xof > 0),
    validated_by      VARCHAR(255),
    validated_at      TIMESTAMP,

    -- Paiement manuel déclaré par le client (Wave, Orange Money, virement) puis confirmé par la Finance
    manual_method     VARCHAR(20),
    manual_reference  VARCHAR(120),
    declared_at       TIMESTAMP,
    proof_object_key  VARCHAR(512),
    reject_note       TEXT,
    confirmed_by      VARCHAR(255),
    confirmed_at      TIMESTAMP,
    paid_at           TIMESTAMP,
    paid_amount_xof   BIGINT,
    paid_channel      VARCHAR(10),
    receipt_url       VARCHAR(1024),

    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP
);

CREATE INDEX idx_coaching_request_status ON coaching_request (status, created_at DESC);
CREATE INDEX idx_coaching_request_email ON coaching_request (email);

-- Tentatives de paiement en ligne d'une demande de coaching (montant figé : prix + frais éventuels)
CREATE TABLE coaching_checkout (
    id                  UUID         PRIMARY KEY,
    public_ref          UUID         NOT NULL UNIQUE,
    coaching_request_id UUID         NOT NULL REFERENCES coaching_request (id),
    amount_xof          BIGINT       NOT NULL CHECK (amount_xof > 0),
    fee_xof             BIGINT       NOT NULL DEFAULT 0 CHECK (fee_xof >= 0),
    charged_xof         BIGINT       NOT NULL CHECK (charged_xof >= amount_xof),
    status              VARCHAR(12)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED')),
    provider            VARCHAR(20)  NOT NULL,
    provider_mode       VARCHAR(10),
    provider_token      VARCHAR(255) UNIQUE,
    checkout_url        VARCHAR(1024),
    receipt_url         VARCHAR(1024),
    failure_reason      TEXT,
    last_checked_at     TIMESTAMP,
    paid_at             TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_coaching_checkout_request ON coaching_checkout (coaching_request_id);
CREATE INDEX idx_coaching_checkout_pending ON coaching_checkout (status, created_at);

-- Droit de traiter les demandes de coaching : profil Pédagogie (responsable pédagogique) ; la Direction a tous les droits
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'COACHING_MANAGE' FROM roles r WHERE r.name = 'ROLE_PEDAGOGY';
-- L'ancien profil « Gestion complète » garde tout ce qui n'est pas réservé à la Direction
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'COACHING_MANAGE' FROM roles r WHERE r.name = 'ROLE_ADMIN';
