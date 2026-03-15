-- ============================================================
-- V14__Add_country_profile_school_to_registrations.sql
-- Ajout des champs profil apprenant sur les inscriptions bootcamp
-- ============================================================

-- 1. Pays de provenance (texte libre normalisé côté front)
ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS country TEXT;

-- 2. Profil de l'inscrit (enum Java → stocké en TEXT avec contrainte CHECK)
ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS profile TEXT
        CONSTRAINT registrations_profile_check
            CHECK (profile IN ('STUDENT', 'PROFESSIONAL', 'ENTREPRENEUR'));

-- 3. École / institution (obligatoire pour STUDENT, null sinon)
ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS school TEXT;

-- NOTE : les colonnes company et position existaient déjà dans V1.
-- Pour PROFESSIONAL elles deviennent obligatoires côté applicatif (validation Bean Validation),
-- mais on ne pose pas de contrainte NOT NULL en base pour garder la compatibilité
-- avec les anciennes inscriptions qui n'avaient pas ces champs.

-- 4. Token reCAPTCHA : NON persisté en base (validé à la réception, non conservé)
-- Aucune colonne supplémentaire nécessaire.