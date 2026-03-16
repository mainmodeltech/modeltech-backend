-- ============================================================
-- V15__Create_networking_module.sql
-- Module Networking : Alumni, Projets, Membres, Screenshots
-- ============================================================

-- ─── 1. Alumni ────────────────────────────────────────────────────────────────
DROP TABLE  IF EXISTS alumni CASCADE;
CREATE TABLE IF NOT EXISTS alumni (
                        id                 UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,

    -- Lien optionnel vers une inscription existante
                        registration_id    UUID         REFERENCES registrations(id) ON DELETE SET NULL,

    -- Identité
                        name               TEXT         NOT NULL,
                        email              TEXT,
                        phone              TEXT,

    -- Poste actuel
                        current_title      TEXT,
                        current_position   TEXT,

    -- Réseaux / médias
                        linkedin_url       TEXT,
                        photo_url          TEXT,

    -- Promotion
                        cohort             TEXT,
                        year               INTEGER,

    -- Bootcamp suivi (référence souple — pas FK pour éviter dépendance forte)
                        bootcamp_title     TEXT,

    -- Visibilité
                        published          BOOLEAN      NOT NULL DEFAULT true,
                        display_order      INTEGER      NOT NULL DEFAULT 0,

    -- Audit
                        created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        created_by         TEXT,
                        updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        updated_by         TEXT,
                        is_deleted         BOOLEAN      NOT NULL DEFAULT false,
                        deleted_at         TIMESTAMPTZ,
                        deleted_by         TEXT
);


CREATE INDEX IF NOT EXISTS idx_alumni_published       ON alumni (published) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_alumni_registration_id ON alumni (registration_id);

-- ─── 2. Projects ──────────────────────────────────────────────────────────────
DROP TABLE  IF EXISTS projects CASCADE;
CREATE TABLE IF NOT EXISTS projects (
                          id                  UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,

    -- Contenu
                          title               TEXT         NOT NULL,
                          description         TEXT,
                          tools_technologies  TEXT[]       NOT NULL DEFAULT '{}',
                          access_link         TEXT,
                          cover_image_url     TEXT,

    -- Classification
                          cohort              TEXT,
                          year                INTEGER,

    -- Visibilité
                          published           BOOLEAN      NOT NULL DEFAULT true,
                          display_order       INTEGER      NOT NULL DEFAULT 0,

    -- Audit
                          created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          created_by          TEXT,
                          updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          updated_by          TEXT,
                          is_deleted          BOOLEAN      NOT NULL DEFAULT false,
                          deleted_at          TIMESTAMPTZ,
                          deleted_by          TEXT
);

CREATE INDEX IF NOT EXISTS idx_projects_published ON projects (published) WHERE is_deleted = false;

-- ─── 3. Project Members (jointure Alumni ↔ Project) ──────────────────────────
DROP TABLE  IF EXISTS project_members CASCADE;
CREATE TABLE IF NOT EXISTS project_members (
                                 id             UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                 project_id     UUID         NOT NULL REFERENCES projects(id)  ON DELETE CASCADE,
                                 alumni_id      UUID         NOT NULL REFERENCES alumni(id)    ON DELETE CASCADE,
                                 role           TEXT,                         -- rôle dans le projet (optionnel)
                                 display_order  INTEGER      NOT NULL DEFAULT 0,
                                 created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                 CONSTRAINT uq_project_alumni UNIQUE (project_id, alumni_id)
);

CREATE INDEX IF NOT EXISTS idx_project_members_project ON project_members (project_id);
CREATE INDEX IF NOT EXISTS idx_project_members_alumni  ON project_members (alumni_id);

-- ─── 4. Project Screenshots ───────────────────────────────────────────────────
DROP TABLE  IF EXISTS project_screenshots CASCADE;
CREATE TABLE IF NOT EXISTS project_screenshots (
                                     id             UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                     project_id     UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                     photo_url      TEXT         NOT NULL,         -- URL MinIO (bucket media)
                                     object_key     TEXT         NOT NULL,         -- clé MinIO pour suppression
                                     caption        TEXT,
                                     display_order  INTEGER      NOT NULL DEFAULT 0,
                                     created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_project_screenshots_project ON project_screenshots (project_id);