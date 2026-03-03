-- ============================================================
-- V4 : Ajout de la table bootcamp_sessions
-- Les sessions représentent les cohortes planifiées d'un bootcamp
-- ============================================================

-- Ajout de colonnes manquantes sur bootcamps si pas encore présentes
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS display_order INTEGER DEFAULT 0;
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS tag TEXT; -- ex: "Bestseller", "Nouveau"
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS icon_name TEXT; -- ex: "BarChart3", "Database"
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'data'
    CHECK (category IN ('data', 'bi', 'python', 'sql', 'ai'));

-- Audit columns si BaseEntity non mappée sur bootcamps
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT false;
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE bootcamps ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

-- ============================================================
-- TABLE BOOTCAMP_SESSIONS
-- Une session = une cohorte planifiée d'un bootcamp
-- ============================================================
CREATE TABLE IF NOT EXISTS bootcamp_sessions (
                                                 id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,

    -- Lien au bootcamp parent
                                                 bootcamp_id UUID NOT NULL REFERENCES bootcamps(id) ON DELETE CASCADE,

    -- Identité de la session
                                                 session_name    VARCHAR(255),           -- ex: "Cohorte 5", "Promo Janvier 2025"
                                                 cohort_number   INTEGER,                -- numéro de cohorte (5, 6...)
                                                 year            INTEGER,                -- année (2025)

    -- Dates
                                                 start_date      DATE,                   -- date de début
                                                 end_date        DATE,                   -- date de fin
                                                 registration_deadline DATE,             -- limite d'inscription

    -- Capacité
                                                 max_participants INTEGER DEFAULT 20,
                                                 current_participants INTEGER DEFAULT 0,
                                                 is_full BOOLEAN DEFAULT false,

    -- État
                                                 status VARCHAR(50) DEFAULT 'upcoming'
                                                     CHECK (status IN ('draft', 'upcoming', 'open', 'closed', 'in_progress', 'completed', 'cancelled')),

    -- Lieu / format
                                                 format VARCHAR(50) DEFAULT 'presentiel'
                                                     CHECK (format IN ('presentiel', 'remote', 'hybrid')),
                                                 location TEXT,                          -- ex: "Dakar, Sénégal"

    -- Prix spécifique à la session (peut différer du prix par défaut du bootcamp)
                                                 price_override TEXT,                    -- null = utilise le prix du bootcamp
                                                 early_bird_price TEXT,                  -- prix early bird
                                                 early_bird_deadline DATE,

    -- Visibilité
                                                 is_featured BOOLEAN DEFAULT false,      -- "Prochaine session" mise en avant
                                                 published BOOLEAN DEFAULT true,

    -- Audit (BaseEntity)
                                                 created_at      TIMESTAMP NOT NULL DEFAULT now(),
                                                 updated_at      TIMESTAMP NOT NULL DEFAULT now(),
                                                 created_by      VARCHAR(255),
                                                 updated_by      VARCHAR(255),
                                                 is_deleted      BOOLEAN DEFAULT false,
                                                 deleted_at      TIMESTAMP,
                                                 deleted_by      VARCHAR(255)
);

-- Index
CREATE INDEX IF NOT EXISTS idx_bootcamp_sessions_bootcamp_id ON bootcamp_sessions(bootcamp_id);
CREATE INDEX IF NOT EXISTS idx_bootcamp_sessions_status ON bootcamp_sessions(status);
CREATE INDEX IF NOT EXISTS idx_bootcamp_sessions_start_date ON bootcamp_sessions(start_date);
CREATE INDEX IF NOT EXISTS idx_bootcamp_sessions_published ON bootcamp_sessions(published);

-- Trigger updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_bootcamp_sessions_updated_at ON bootcamp_sessions;
CREATE TRIGGER update_bootcamp_sessions_updated_at
    BEFORE UPDATE ON bootcamp_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();