-- ─────────────────────────────────────────────────────────────────────────────
-- Migration additive : contenu riche de la fiche bootcamp (fiche formation +
-- sélecteur de session) pour aligner l'API avec le nouveau frontend.
-- Toutes les colonnes sont nullable : aucune régression sur les données ou
-- les clients existants (ancien frontend compris).
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE bootcamps
    ADD COLUMN IF NOT EXISTS tagline       TEXT,
    ADD COLUMN IF NOT EXISTS color_key     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS profiles      JSONB,
    ADD COLUMN IF NOT EXISTS tools         JSONB,
    ADD COLUMN IF NOT EXISTS curriculum    JSONB,
    ADD COLUMN IF NOT EXISTS outcomes      JSONB,
    ADD COLUMN IF NOT EXISTS certification JSONB;

ALTER TABLE bootcamp_sessions
    ADD COLUMN IF NOT EXISTS schedule TEXT;

-- Lien optionnel entre un témoignage et le bootcamp concerné (remplace
-- progressivement le champ texte libre "bootcamp" pour l'affichage sur la
-- fiche formation ; le champ texte libre est conservé pour compatibilité).
ALTER TABLE testimonials
    ADD COLUMN IF NOT EXISTS bootcamp_id UUID REFERENCES bootcamps(id);

CREATE INDEX IF NOT EXISTS idx_testimonials_bootcamp_id ON testimonials(bootcamp_id);
