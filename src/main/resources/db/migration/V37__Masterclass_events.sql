-- Masterclass : un vrai objet « événement » (une par mois, ou tous les deux mois) au lieu d'un identifiant de texte lu dans
-- le contenu du site. Les inscriptions restent rattachées par le code de l'événement (masterclass_registrations.masterclass_id).

CREATE TABLE masterclasses (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code                VARCHAR(80)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT,
    starts_at           TIMESTAMP     NOT NULL,
    duration_minutes    INTEGER       NOT NULL DEFAULT 120 CHECK (duration_minutes BETWEEN 15 AND 600),
    format              VARCHAR(10)   NOT NULL DEFAULT 'ONLINE' CHECK (format IN ('ONLINE', 'ONSITE', 'HYBRID')),
    place               VARCHAR(255),
    join_url            VARCHAR(500),
    capacity            INTEGER       CHECK (capacity IS NULL OR capacity > 0),
    published           BOOLEAN       NOT NULL DEFAULT FALSE,
    registrations_closed BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(255),
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(255),
    CONSTRAINT uq_masterclasses_code UNIQUE (code)
);

CREATE INDEX idx_masterclasses_starts ON masterclasses (starts_at) WHERE is_deleted = FALSE;

-- Présence à l'événement, renseignée après coup par l'équipe
ALTER TABLE masterclass_registrations ADD COLUMN attendance VARCHAR(12) NOT NULL DEFAULT 'REGISTERED'
    CHECK (attendance IN ('REGISTERED', 'ATTENDED', 'ABSENT'));

-- Les masterclass déjà ouvertes aux inscriptions deviennent des événements archivés (non publiés), pour garder l'historique
INSERT INTO masterclasses (code, title, starts_at, published, registrations_closed)
SELECT d.masterclass_id, 'Masterclass ' || d.masterclass_id, COALESCE(min(r.created_at), NOW()), FALSE, TRUE
FROM (SELECT DISTINCT masterclass_id FROM masterclass_registrations WHERE is_deleted = FALSE) d
JOIN masterclass_registrations r ON r.masterclass_id = d.masterclass_id
GROUP BY d.masterclass_id;

-- Droit de gérer les masterclass (créer, modifier, écrire aux inscrits, présences)
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'MASTERCLASS_MANAGE' FROM roles r WHERE r.name IN ('ROLE_COMMUNICATION', 'ROLE_PEDAGOGY', 'ROLE_ADMIN');
