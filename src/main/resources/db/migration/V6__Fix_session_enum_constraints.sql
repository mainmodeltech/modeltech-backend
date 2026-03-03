-- ============================================================
-- V5 : Correction des contraintes CHECK format et status
-- Alignement avec les valeurs Java @Enumerated(EnumType.STRING)
-- qui stocke les noms d'enum en MAJUSCULES
-- ============================================================

-- Supprimer les anciennes contraintes (minuscules)
ALTER TABLE bootcamp_sessions DROP CONSTRAINT IF EXISTS bootcamp_sessions_format_check;
ALTER TABLE bootcamp_sessions DROP CONSTRAINT IF EXISTS bootcamp_sessions_status_check;

-- Remettre les valeurs existantes en majuscules (si des données existent déjà)
UPDATE bootcamp_sessions SET format = UPPER(format) WHERE format ~ '^[a-z]';
UPDATE bootcamp_sessions SET status = UPPER(status) WHERE status ~ '^[a-z]';

-- Recréer les contraintes en MAJUSCULES (cohérent avec EnumType.STRING)
ALTER TABLE bootcamp_sessions
    ADD CONSTRAINT bootcamp_sessions_format_check
        CHECK (format IN ('PRESENTIEL', 'REMOTE', 'HYBRID'));

ALTER TABLE bootcamp_sessions
    ADD CONSTRAINT bootcamp_sessions_status_check
        CHECK (status IN ('DRAFT', 'UPCOMING', 'OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- Corriger aussi les valeurs par défaut
ALTER TABLE bootcamp_sessions ALTER COLUMN format SET DEFAULT 'PRESENTIEL';
ALTER TABLE bootcamp_sessions ALTER COLUMN status SET DEFAULT 'UPCOMING';