-- Certificats : validation manuelle avant envoi.
-- Un apprenant qui remplit les conditions obtient un certificat « A valider » (PENDING) : invisible de l'apprenant, non vérifiable
-- publiquement, aucun e-mail. Une personne habilitée le prévisualise puis le valide (VALID : envoi et visibilité) ou le refuse (REVOKED).

ALTER TABLE certificates DROP CONSTRAINT IF EXISTS certificates_status_check;
ALTER TABLE certificates ADD CONSTRAINT certificates_status_check CHECK (status IN ('PENDING', 'VALID', 'REVOKED'));

-- Date à laquelle l'apprenant est devenu éligible (issued_at devient la date de validation/délivrance)
ALTER TABLE certificates ADD COLUMN eligible_at TIMESTAMP;
UPDATE certificates SET eligible_at = issued_at;

-- Un seul certificat en attente ou valide par apprenant et formation
DROP INDEX IF EXISTS uq_certificates_valid;
CREATE UNIQUE INDEX uq_certificates_valid ON certificates (learner_id, bootcamp_id)
    WHERE status IN ('VALID', 'PENDING') AND is_deleted = FALSE;
