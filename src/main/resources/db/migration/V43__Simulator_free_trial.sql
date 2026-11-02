-- Simulateur de certification (lot S2b) : essai gratuit, produit d'appel.
-- 1. Les questions « échantillon » sont celles, et seulement celles, que voit un candidat en essai gratuit : toujours le même
--    petit lot, pour qu'on ne puisse pas aspirer la banque en créant des comptes à la chaîne. Le marquage porte sur l'identité
--    de la question (pas sur sa version) : une version publiée reste figée.
ALTER TABLE sim_item ADD COLUMN trial_sample BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_sim_item_trial_sample ON sim_item (trial_sample) WHERE trial_sample;

-- 2. Un seul essai gratuit par personne, garanti par la base (même révoqué ou expiré).
CREATE UNIQUE INDEX uq_sim_one_trial_per_learner ON sim_access_grant (learner_id) WHERE plan_code = 'TRIAL';

-- 3. Chaque demande d'essai est un prospect : elle apparaît dans « Messages » avec un type dédié.
ALTER TABLE contact_messages DROP CONSTRAINT contact_messages_type_check;
ALTER TABLE contact_messages
    ADD CONSTRAINT contact_messages_type_check
        CHECK (type IN ('CONTACT', 'DIAGNOSTIC', 'PARTNER_APPLICATION', 'SIMULATOR_WAITLIST', 'SIMULATOR_TRIAL'));
