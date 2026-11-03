-- Paiements (lot P1) : réglages de paiement choisis dans le back-office (par la Direction), et paiement MANUEL des commandes du simulateur.

-- 1. Interrupteur général du paiement en ligne (une seule ligne). Activé : il ne fait rien sans clés de passerelle ; il sert à couper en urgence.
CREATE TABLE payment_setting (
    id             SMALLINT  NOT NULL PRIMARY KEY CHECK (id = 1),
    online_enabled BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(255)
);
INSERT INTO payment_setting (id, online_enabled) VALUES (1, TRUE);

-- 2. Moyens de paiement par service. Valeurs de départ = comportement actuel : le simulateur se paie en ligne (manuel en secours si la
--    passerelle est indisponible), les inscriptions et le coaching restent en paiement manuel.
CREATE TABLE payment_service_setting (
    service    VARCHAR(20) NOT NULL PRIMARY KEY CHECK (service IN ('SIMULATOR', 'REGISTRATION', 'COACHING')),
    mode       VARCHAR(20) NOT NULL CHECK (mode IN ('MANUAL_ONLY', 'ONLINE_AND_MANUAL', 'ONLINE_ONLY')),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
INSERT INTO payment_service_setting (service, mode) VALUES
    ('SIMULATOR', 'ONLINE_ONLY'),
    ('REGISTRATION', 'MANUAL_ONLY'),
    ('COACHING', 'MANUAL_ONLY');

-- 3. Commandes du simulateur : canal (en ligne ou manuel), frais éventuels, déclaration manuelle et justificatif.
--    amount_xof = prix du catalogue ; charged_xof = montant réellement encaissé en ligne (prix + frais si on les répercute).
ALTER TABLE sim_order ADD COLUMN channel VARCHAR(10) NOT NULL DEFAULT 'ONLINE' CHECK (channel IN ('ONLINE', 'MANUAL'));
ALTER TABLE sim_order ADD COLUMN fee_xof INTEGER NOT NULL DEFAULT 0 CHECK (fee_xof >= 0);
ALTER TABLE sim_order ADD COLUMN charged_xof INTEGER;
UPDATE sim_order SET charged_xof = amount_xof;
ALTER TABLE sim_order ALTER COLUMN charged_xof SET NOT NULL;
ALTER TABLE sim_order ADD COLUMN manual_method VARCHAR(20) CHECK (manual_method IS NULL OR manual_method IN ('WAVE', 'ORANGE_MONEY', 'VIREMENT'));
ALTER TABLE sim_order ADD COLUMN manual_reference VARCHAR(120);
ALTER TABLE sim_order ADD COLUMN declared_at TIMESTAMP;
ALTER TABLE sim_order ADD COLUMN proof_object_key VARCHAR(512);
ALTER TABLE sim_order ADD COLUMN confirmed_by VARCHAR(255);
ALTER TABLE sim_order ADD COLUMN confirmed_at TIMESTAMP;

-- Nouvel état : DECLARED = paiement manuel déclaré par l'acheteur, en attente de confirmation par l'équipe.
ALTER TABLE sim_order DROP CONSTRAINT sim_order_status_check;
ALTER TABLE sim_order ADD CONSTRAINT sim_order_status_check CHECK (status IN ('PENDING', 'DECLARED', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED'));
