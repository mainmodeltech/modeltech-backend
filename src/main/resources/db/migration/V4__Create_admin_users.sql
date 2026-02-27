-- ============================================================
-- V4 : Table des utilisateurs administrateurs
-- Utilisée pour l'authentification JWT du backoffice
-- ============================================================

CREATE TABLE public.admin_users (
                                    id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                    email TEXT NOT NULL UNIQUE,
                                    password_hash TEXT NOT NULL,
                                    full_name TEXT NOT NULL,
                                    role TEXT NOT NULL DEFAULT 'ADMIN',
                                    active BOOLEAN NOT NULL DEFAULT true,
                                    last_login_at TIMESTAMP WITH TIME ZONE,
                                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                    created_by TEXT,
                                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                    updated_by TEXT,
                                    is_deleted BOOLEAN NOT NULL DEFAULT false,
                                    deleted_at TIMESTAMP WITH TIME ZONE,
                                    deleted_by TEXT
);

CREATE TRIGGER tr_admin_users_upd
    BEFORE UPDATE ON public.admin_users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Admin par défaut (mot de passe: Admin@2025! — à changer immédiatement)
-- Hash BCrypt strength 12
INSERT INTO public.admin_users (email, password_hash, full_name, role)
VALUES (
           'admin@model-technologie.com',
           '$2a$12$ejzXgIADu4bc7qMZt1ua6Ogv/nmB1soFb6Na8lf/fNIg5/AWakagm',
           'Administrateur Model Technologie',
           'ADMIN'
       );