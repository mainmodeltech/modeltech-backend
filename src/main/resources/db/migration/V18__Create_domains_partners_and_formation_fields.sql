-- ─────────────────────────────────────────────────────────────────────────────
-- Lot "domain-partner-model" : catalogue de formations par domaines.
--  - tables domains / partners / bootcamp_related
--  - champs formation sur bootcamps (slug, domaine, dispensé par, niveau, format…)
-- Migration additive : aucune colonne existante n'est modifiée ; les données
-- existantes sont rétro-remplies (slug + domaine "Data & BI").
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. DOMAINES
CREATE TABLE IF NOT EXISTS domains (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    slug          VARCHAR(100) NOT NULL UNIQUE,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    badge         VARCHAR(100),
    coming_soon   BOOLEAN      NOT NULL DEFAULT FALSE,
    visible       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(255),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMP,
    deleted_by    VARCHAR(255)
);

-- 2. PARTENAIRES FORMATEURS (part de revenu et contact : usage interne uniquement)
CREATE TABLE IF NOT EXISTS partners (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    slug                   VARCHAR(100) NOT NULL UNIQUE,
    name                   VARCHAR(150) NOT NULL,
    logo_url               TEXT,
    logo_object_key        VARCHAR(255),
    bio                    TEXT,
    website                VARCHAR(255),
    contact_name           VARCHAR(150),
    contact_email          VARCHAR(255),
    contact_phone          VARCHAR(50),
    revenue_share_percent  NUMERIC(5,2),
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(255),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by             VARCHAR(255),
    is_deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at             TIMESTAMP,
    deleted_by             VARCHAR(255),
    CONSTRAINT partners_revenue_share_check
        CHECK (revenue_share_percent IS NULL OR (revenue_share_percent >= 0 AND revenue_share_percent <= 100))
);

-- 3. CHAMPS FORMATION SUR bootcamps
ALTER TABLE bootcamps
    ADD COLUMN IF NOT EXISTS slug               VARCHAR(150),
    ADD COLUMN IF NOT EXISTS domain_id          UUID REFERENCES domains(id),
    ADD COLUMN IF NOT EXISTS delivered_by       VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN IF NOT EXISTS partner_id         UUID REFERENCES partners(id),
    ADD COLUMN IF NOT EXISTS level              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS format             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS certification_prep TEXT,
    ADD COLUMN IF NOT EXISTS target_roles       TEXT[];

ALTER TABLE bootcamps
    ADD CONSTRAINT bootcamps_delivered_by_check CHECK (delivered_by IN ('INTERNAL', 'PARTNER')),
    ADD CONSTRAINT bootcamps_partner_required_check CHECK (delivered_by <> 'PARTNER' OR partner_id IS NOT NULL),
    ADD CONSTRAINT bootcamps_level_check CHECK (level IS NULL OR level IN ('DEBUTANT', 'INTERMEDIAIRE', 'AVANCE')),
    ADD CONSTRAINT bootcamps_format_check CHECK (format IS NULL OR format IN ('PRESENTIEL', 'EN_LIGNE', 'HYBRIDE'));

-- 4. FORMATIONS LIÉES (relatedFormationIds)
CREATE TABLE IF NOT EXISTS bootcamp_related (
    bootcamp_id         UUID NOT NULL REFERENCES bootcamps(id) ON DELETE CASCADE,
    related_bootcamp_id UUID NOT NULL REFERENCES bootcamps(id) ON DELETE CASCADE,
    PRIMARY KEY (bootcamp_id, related_bootcamp_id),
    CONSTRAINT bootcamp_related_no_self CHECK (bootcamp_id <> related_bootcamp_id)
);

-- 5. RÉTRO-REMPLISSAGE
-- 5a. Domaine historique : toutes les formations existantes sont Data & BI.
INSERT INTO domains (slug, name, description, badge, coming_soon, visible, display_order)
VALUES ('data-bi', 'Data & BI',
        'Power BI, Python, SQL et Excel Finance — conçues et dispensées par Model Technologie.',
        'NOTRE SPÉCIALITÉ', FALSE, TRUE, 0)
ON CONFLICT (slug) DO NOTHING;

UPDATE bootcamps
SET domain_id = (SELECT id FROM domains WHERE slug = 'data-bi')
WHERE domain_id IS NULL;

-- 5b. Slug déterministe depuis le titre (sans accents, tirets), unique via suffixe -2, -3…
WITH base AS (
    SELECT id,
           created_at,
           COALESCE(NULLIF(
               trim(both '-' from regexp_replace(
                   lower(translate(title,
                       'àâäéèêëîïôöùûüçÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ',
                       'aaaeeeeiioouuucAAAEEEEIIOOUUUC')),
                   '[^a-z0-9]+', '-', 'g')),
               ''), 'formation') AS s
    FROM bootcamps
    WHERE slug IS NULL
),
ranked AS (
    SELECT id, s, row_number() OVER (PARTITION BY s ORDER BY created_at, id) AS rn
    FROM base
)
UPDATE bootcamps b
SET slug = CASE WHEN r.rn = 1 THEN r.s ELSE r.s || '-' || r.rn END
FROM ranked r
WHERE b.id = r.id;

ALTER TABLE bootcamps ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_bootcamps_slug ON bootcamps(slug);
CREATE INDEX IF NOT EXISTS idx_bootcamps_domain_id ON bootcamps(domain_id);
CREATE INDEX IF NOT EXISTS idx_bootcamps_partner_id ON bootcamps(partner_id);
