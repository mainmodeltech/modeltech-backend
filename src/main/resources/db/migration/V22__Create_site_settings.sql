-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "site-content" : contenus du site éditables depuis le back-office
-- (tarifs du coaching, coach, prochain atelier, étude de cas…).
-- Aucune donnée n'est insérée : tant qu'une clé n'existe pas, le site masque le bloc.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE site_settings (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL,
    value       JSONB        NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_site_settings_key UNIQUE (setting_key),
    CONSTRAINT site_settings_key_format CHECK (setting_key ~ '^[a-z0-9][a-z0-9._-]*$')
);
