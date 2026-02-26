-- =============================================================
-- V2 : Ajout des colonnes d'audit manquantes sur la table services
-- Necessaire pour la compatibilite avec BaseEntity (JPA Auditing)
-- =============================================================

ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS updated_by TEXT;
ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS deleted_by TEXT;
