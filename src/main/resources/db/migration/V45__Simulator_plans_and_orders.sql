-- Simulateur de certification (lot S3) : forfaits en vente et commandes payées en ligne.
-- Les prix sont des HYPOTHÈSES à valider (précommandes) : modifiables à tout moment dans l'administration, sans déploiement.

CREATE TABLE sim_plan (
    code          VARCHAR(20)  NOT NULL PRIMARY KEY CHECK (code IN ('ESSENTIEL', 'PRO', 'PRO_COACHING')),
    name          VARCHAR(80)  NOT NULL,
    price_xof     INTEGER      NOT NULL CHECK (price_xof BETWEEN 100 AND 5000000),
    duration_days INTEGER      NOT NULL CHECK (duration_days BETWEEN 1 AND 365),
    exam_attempts INTEGER      NOT NULL CHECK (exam_attempts BETWEEN 0 AND 50),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INTEGER      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP,
    updated_by    VARCHAR(255)
);

INSERT INTO sim_plan (code, name, price_xof, duration_days, exam_attempts, display_order) VALUES
    ('ESSENTIEL',    'Essentiel',      25000, 30, 2, 1),
    ('PRO',          'Pro',            45000, 90, 5, 2),
    ('PRO_COACHING', 'Pro + coaching', 75000, 90, 5, 3);

-- Une commande = une intention d'achat. Elle ne devient « payée » qu'après vérification auprès du prestataire (jamais sur la
-- foi du navigateur ni d'une notification seule). Le montant, la durée et le quota sont figés à la commande.
CREATE TABLE sim_order (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    public_ref      UUID         NOT NULL UNIQUE,                       -- référence opaque (adresse de retour), non devinable
    learner_id      UUID         NOT NULL REFERENCES learners (id),
    plan_code       VARCHAR(20)  NOT NULL REFERENCES sim_plan (code),
    plan_name       VARCHAR(80)  NOT NULL,
    amount_xof      INTEGER      NOT NULL CHECK (amount_xof > 0),
    duration_days   INTEGER      NOT NULL CHECK (duration_days > 0),
    exam_attempts   INTEGER      NOT NULL CHECK (exam_attempts >= 0),
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED')),
    provider        VARCHAR(20)  NOT NULL,
    provider_mode   VARCHAR(10),
    provider_token  VARCHAR(120) UNIQUE,
    checkout_url    TEXT,
    receipt_url     TEXT,
    failure_reason  TEXT,
    grant_id        UUID         UNIQUE REFERENCES sim_access_grant (id),  -- au plus un accès par commande : paiement notifié deux fois = un seul accès
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    paid_at         TIMESTAMP,
    last_checked_at TIMESTAMP,
    version         INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_sim_order_status ON sim_order (status, created_at);
CREATE INDEX idx_sim_order_learner ON sim_order (learner_id, created_at DESC);
