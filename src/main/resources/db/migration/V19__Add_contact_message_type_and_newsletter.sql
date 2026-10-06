-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "site-forms" : formulaires du site (diagnostic entreprise, candidature
-- partenaire, contact avec type de demandeur) et newsletter.
-- Additive : les messages existants deviennent de type CONTACT.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Les messages de contact portent un type, un type de demandeur et un détail structuré
ALTER TABLE contact_messages
    ADD COLUMN IF NOT EXISTS type           VARCHAR(30) NOT NULL DEFAULT 'CONTACT',
    ADD COLUMN IF NOT EXISTS requester_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS details        JSONB;

ALTER TABLE contact_messages
    ADD CONSTRAINT contact_messages_type_check
        CHECK (type IN ('CONTACT', 'DIAGNOSTIC', 'PARTNER_APPLICATION')),
    ADD CONSTRAINT contact_messages_requester_type_check
        CHECK (requester_type IS NULL OR requester_type IN ('PARTICULIER', 'ENTREPRISE'));

CREATE INDEX IF NOT EXISTS idx_contact_messages_type ON contact_messages(type);

-- 2. Abonnements newsletter (double opt-in)
CREATE TABLE IF NOT EXISTS newsletter_subscriptions (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email                   VARCHAR(255) NOT NULL UNIQUE,            -- stocké en minuscules
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    source                  VARCHAR(50),
    confirmation_token      VARCHAR(64)  UNIQUE,
    confirmation_expires_at TIMESTAMP,
    unsubscribe_token       VARCHAR(64)  NOT NULL UNIQUE,
    confirmed_at            TIMESTAMP,
    unsubscribed_at         TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(255),
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              VARCHAR(255),
    CONSTRAINT newsletter_subscriptions_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'UNSUBSCRIBED'))
);

CREATE INDEX IF NOT EXISTS idx_newsletter_subscriptions_status ON newsletter_subscriptions(status);
