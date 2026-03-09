-- ─────────────────────────────────────────────────────────────────────────────
-- Migration : ajout des colonnes bootcamp et result à la table testimonials
-- À exécuter dans PostgreSQL (Flyway, Liquibase, ou psql direct)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.testimonials
    ADD COLUMN IF NOT EXISTS bootcamp TEXT,         -- ex: 'Power BI', 'Data Analyst'
    ADD COLUMN IF NOT EXISTS result   TEXT;         -- ex: 'Embauché en 3 mois après le bootcamp'

COMMENT ON COLUMN public.testimonials.bootcamp IS 'Programme suivi par l''auteur du témoignage';
COMMENT ON COLUMN public.testimonials.result   IS 'Résultat concret mis en avant (accroche)';