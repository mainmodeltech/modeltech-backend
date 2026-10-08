-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "notifications-reliability" : journal des e-mails sortants (métadonnées seulement).
-- Le corps n'est JAMAIS stocké : plusieurs e-mails contiennent un lien de définition de
-- mot de passe, qu'un lecteur du journal pourrait réutiliser.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE email_logs (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    type       VARCHAR(50)  NOT NULL,
    recipient  VARCHAR(255) NOT NULL,
    subject    VARCHAR(500),
    status     VARCHAR(10)  NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    attempts   INTEGER      NOT NULL DEFAULT 1,
    error      VARCHAR(1000),
    sent_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255)
);

CREATE INDEX idx_email_logs_created ON email_logs (created_at DESC);
CREATE INDEX idx_email_logs_status  ON email_logs (status) WHERE status = 'FAILED';
