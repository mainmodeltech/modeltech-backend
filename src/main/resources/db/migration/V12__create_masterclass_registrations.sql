-- V12__create_masterclass_registrations.sql

CREATE TABLE IF NOT EXISTS public.masterclass_registrations
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    masterclass_id   VARCHAR(100) NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    phone            VARCHAR(30)  NOT NULL,
    profile          VARCHAR(50),
    company          VARCHAR(150),
    email_sent       BOOLEAN      NOT NULL DEFAULT FALSE,
    slack_notified   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    deleted_by       VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMP   ,
    CONSTRAINT uq_masterclass_email UNIQUE (masterclass_id, email)
);

CREATE INDEX IF NOT EXISTS idx_masterclass_reg_mid ON public.masterclass_registrations (masterclass_id);
CREATE INDEX IF NOT EXISTS idx_masterclass_reg_email ON public.masterclass_registrations (email);

COMMENT ON TABLE public.masterclass_registrations IS 'Inscriptions aux masterclasses gratuites Model Technologie';