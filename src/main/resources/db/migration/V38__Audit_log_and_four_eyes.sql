-- Phase 3b : journal d'audit (qui a fait quoi, quand, d'où) — append-only.
-- Phase 3c : séparation des tâches (remboursement demandé puis validé ; paiement saisi hors site puis confirmé par une autre personne).

CREATE TABLE audit_log (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    occurred_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    actor_email    VARCHAR(255) NOT NULL,
    actor_name     VARCHAR(255),
    actor_profiles VARCHAR(255),
    action         VARCHAR(60)  NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    entity_type    VARCHAR(40),
    entity_id      VARCHAR(64),
    summary        TEXT         NOT NULL,
    details        JSONB,
    flag           VARCHAR(30),
    ip             VARCHAR(64)
);

CREATE INDEX idx_audit_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_actor    ON audit_log (actor_email, occurred_at DESC);
CREATE INDEX idx_audit_action   ON audit_log (action, occurred_at DESC);
CREATE INDEX idx_audit_entity   ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_flag     ON audit_log (flag, occurred_at DESC) WHERE flag IS NOT NULL;

-- Un journal qu'on peut réécrire ne prouve rien : ni modification ni suppression, même par l'application.
CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Le journal d''audit est en ajout seul : modification et suppression interdites';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

-- 3c : qui a saisi le paiement hors site, qui a demandé le remboursement, et si le second contrôle a manqué
ALTER TABLE payments ADD COLUMN recorded_by             VARCHAR(255);
ALTER TABLE payments ADD COLUMN confirmed_unchecked     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payments ADD COLUMN refund_requested_at     TIMESTAMP;
ALTER TABLE payments ADD COLUMN refund_requested_by     VARCHAR(255);
ALTER TABLE payments ADD COLUMN refund_request_reason   TEXT;
ALTER TABLE payments ADD COLUMN refund_unchecked        BOOLEAN NOT NULL DEFAULT FALSE;

-- Nouveaux droits : saisir un paiement hors site (Admissions, Finance) et valider un remboursement (Finance, Direction).
-- Les profils qui pouvaient déjà gérer les paiements / rembourser les conservent.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, 'PAYMENTS_RECORD' FROM roles r
 WHERE r.name = 'ROLE_ADMISSIONS'
    OR EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = 'PAYMENTS_MANAGE');

INSERT INTO role_permissions (role_id, permission)
SELECT rp.role_id, 'PAYMENTS_REFUND_APPROVE' FROM role_permissions rp WHERE rp.permission = 'PAYMENTS_REFUND';
