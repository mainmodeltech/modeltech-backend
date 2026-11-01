-- Simulateur de certification (lot S1) : banque de questions versionnée, blueprint de la certification,
-- droits dédiés. Les corrigés vivent dans `payload` : jamais exposés par l'API apprenant (lot S2).

CREATE TABLE sim_certification (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    vendor     VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Une version du guide de compétences (ex. « compétences mesurées au 20 avril 2026 »)
CREATE TABLE sim_blueprint (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    certification_id UUID         NOT NULL REFERENCES sim_certification (id),
    version_date     DATE         NOT NULL,
    label            VARCHAR(200) NOT NULL,
    duration_minutes INTEGER      NOT NULL CHECK (duration_minutes > 0),
    questions_min    INTEGER      NOT NULL CHECK (questions_min > 0),
    questions_max    INTEGER      NOT NULL CHECK (questions_max >= questions_min),
    pass_score       INTEGER      NOT NULL CHECK (pass_score BETWEEN 1 AND 1000),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sim_blueprint UNIQUE (certification_id, version_date)
);

CREATE TABLE sim_blueprint_domain (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    blueprint_id UUID         NOT NULL REFERENCES sim_blueprint (id),
    code         VARCHAR(10)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    weight_min   INTEGER      NOT NULL CHECK (weight_min BETWEEN 0 AND 100),
    weight_max   INTEGER      NOT NULL CHECK (weight_max BETWEEN weight_min AND 100),
    position     INTEGER      NOT NULL,
    CONSTRAINT uq_sim_blueprint_domain UNIQUE (blueprint_id, code)
);

CREATE TABLE sim_blueprint_skill (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    domain_id        UUID         NOT NULL REFERENCES sim_blueprint_domain (id),
    code             VARCHAR(30)  NOT NULL,
    name             VARCHAR(300) NOT NULL,
    position         INTEGER      NOT NULL,
    target_questions INTEGER      NOT NULL DEFAULT 0 CHECK (target_questions >= 0),
    CONSTRAINT uq_sim_blueprint_skill UNIQUE (domain_id, code)
);

CREATE TABLE sim_item (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    certification_id UUID         NOT NULL REFERENCES sim_certification (id),
    skill_id         UUID         NOT NULL REFERENCES sim_blueprint_skill (id),
    public_code      VARCHAR(40)  NOT NULL UNIQUE,
    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sim_item_skill ON sim_item (skill_id);

CREATE TABLE sim_item_version (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    item_id        UUID         NOT NULL REFERENCES sim_item (id),
    version_no     INTEGER      NOT NULL CHECK (version_no > 0),
    type           VARCHAR(20)  NOT NULL CHECK (type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'YES_NO_SERIES')),
    difficulty     SMALLINT     NOT NULL CHECK (difficulty BETWEEN 1 AND 3),
    stem_en        TEXT         NOT NULL,
    payload        JSONB        NOT NULL,
    explanation_fr TEXT,
    status         VARCHAR(12)  NOT NULL CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'RETIRED')),
    blueprint_id   UUID         NOT NULL REFERENCES sim_blueprint (id),
    author         VARCHAR(255) NOT NULL,
    reviewer       VARCHAR(255),
    review_note    TEXT,
    verified_by    VARCHAR(255),
    verified_at    TIMESTAMP,
    publisher      VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP,
    published_at   TIMESTAMP,
    retired_at     TIMESTAMP,
    retire_reason  TEXT,
    CONSTRAINT uq_sim_item_version UNIQUE (item_id, version_no)
);
CREATE INDEX idx_sim_item_version_status ON sim_item_version (status);
-- Au plus une version publiée par question
CREATE UNIQUE INDEX uq_sim_item_one_published ON sim_item_version (item_id) WHERE status = 'PUBLISHED';

-- Une version publiée ou retirée est figée : seule la mise hors service (PUBLISHED -> RETIRED) est possible.
CREATE FUNCTION sim_item_version_frozen() RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('PUBLISHED', 'RETIRED') THEN
        IF OLD.status = 'RETIRED' THEN
            RAISE EXCEPTION 'Une version retiree ne se modifie plus';
        END IF;
        IF NEW.status NOT IN ('PUBLISHED', 'RETIRED')
           OR NEW.type <> OLD.type OR NEW.difficulty <> OLD.difficulty OR NEW.stem_en <> OLD.stem_en
           OR NEW.payload <> OLD.payload OR NEW.explanation_fr IS DISTINCT FROM OLD.explanation_fr
           OR NEW.item_id <> OLD.item_id OR NEW.version_no <> OLD.version_no OR NEW.author <> OLD.author THEN
            RAISE EXCEPTION 'Une version publiee est figee : creez une nouvelle version';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sim_item_version_frozen BEFORE UPDATE ON sim_item_version
    FOR EACH ROW EXECUTE FUNCTION sim_item_version_frozen();

-- ── Blueprint PL-300 : compétences mesurées au 20 avril 2026 (guide d'étude public de Microsoft) ──
INSERT INTO sim_certification (code, name, vendor) VALUES ('PL-300', 'Microsoft Power BI Data Analyst', 'Microsoft');

INSERT INTO sim_blueprint (certification_id, version_date, label, duration_minutes, questions_min, questions_max, pass_score)
SELECT id, DATE '2026-04-20', 'Compétences mesurées au 20 avril 2026', 100, 40, 60, 700 FROM sim_certification WHERE code = 'PL-300';

INSERT INTO sim_blueprint_domain (blueprint_id, code, name, weight_min, weight_max, position)
SELECT b.id, d.code, d.name, d.wmin, d.wmax, d.pos
FROM sim_blueprint b
JOIN sim_certification c ON c.id = b.certification_id AND c.code = 'PL-300'
CROSS JOIN (VALUES ('PRE', 'Prepare the data', 25, 30, 1),
                   ('MOD', 'Model the data', 25, 30, 2),
                   ('VIS', 'Visualize and analyze the data', 25, 30, 3),
                   ('SEC', 'Manage and secure Power BI', 15, 20, 4)) AS d(code, name, wmin, wmax, pos);

INSERT INTO sim_blueprint_skill (domain_id, code, name, position, target_questions)
SELECT bd.id, s.code, s.name, s.pos, s.target
FROM sim_blueprint_domain bd
JOIN (VALUES
    ('PRE', 'PRE-CONNECT',   'Get or connect to data (storage modes, parameters)', 1, 30),
    ('PRE', 'PRE-CLEAN',     'Profile and clean the data', 2, 30),
    ('PRE', 'PRE-TRANSFORM', 'Transform and load the data', 3, 60),
    ('MOD', 'MOD-DESIGN',    'Design and implement a data model', 1, 36),
    ('MOD', 'MOD-DAX',       'Create model calculations by using DAX', 2, 56),
    ('MOD', 'MOD-OPTIMIZE',  'Optimize model performance', 3, 20),
    ('VIS', 'VIS-REPORTS',   'Create reports', 1, 44),
    ('VIS', 'VIS-ENHANCE',   'Enhance reports for usability and storytelling', 2, 36),
    ('VIS', 'VIS-PATTERNS',  'Identify patterns and trends', 3, 32),
    ('SEC', 'SEC-WORKSPACES','Create and manage workspaces and assets', 1, 28),
    ('SEC', 'SEC-SECURITY',  'Secure and govern Power BI items', 2, 28)
) AS s(domain, code, name, pos, target) ON s.domain = bd.code
WHERE bd.blueprint_id = (SELECT b.id FROM sim_blueprint b JOIN sim_certification c ON c.id = b.certification_id WHERE c.code = 'PL-300');

-- ── Droits du simulateur (profil Pédagogie ; la Direction a tous les droits) ──
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('SIMULATOR_VIEW'), ('SIMULATOR_AUTHOR'), ('SIMULATOR_REVIEW'), ('SIMULATOR_MANAGE')) AS v(p)
 WHERE r.name = 'ROLE_PEDAGOGY';
-- L'ancien profil « Gestion complète » garde tout ce qui n'est pas réservé à la Direction
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, v.p FROM roles r CROSS JOIN (VALUES ('SIMULATOR_VIEW'), ('SIMULATOR_AUTHOR'), ('SIMULATOR_REVIEW'), ('SIMULATOR_MANAGE')) AS v(p)
 WHERE r.name = 'ROLE_ADMIN';
