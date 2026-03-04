-- ============================================================
-- V7 : Alignement des statuts registrations avec l'enum Java
-- Les valeurs en DB sont en minuscules ('pending', 'confirmed'...)
-- L'enum Java utilise EnumType.STRING en MAJUSCULES ('PENDING', 'CONFIRMED'...)
-- ============================================================

-- Convertir les valeurs existantes en majuscules
UPDATE registrations SET status = UPPER(status) WHERE status ~ '^[a-z]';

-- Supprimer l'ancienne contrainte si elle existe
ALTER TABLE registrations DROP CONSTRAINT IF EXISTS registrations_status_check;

-- Ajouter la contrainte CHECK alignee avec l'enum Java
ALTER TABLE registrations
    ADD CONSTRAINT registrations_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));

-- Corriger la valeur par defaut
ALTER TABLE registrations ALTER COLUMN status SET DEFAULT 'PENDING';
