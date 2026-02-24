-- ============================================================
-- SCHÉMA COMPLET CONSOLIDÉ - MODEL TECHNOLOGIE HUB
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. BOOTCAMPS
CREATE TABLE public.bootcamps (
                                  id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                  title TEXT NOT NULL,
                                  description TEXT,
                                  duration TEXT,
                                  audience TEXT,
                                  prerequisites TEXT,
                                  price TEXT,
                                  next_session TEXT,
                                  benefits TEXT[],
                                  featured BOOLEAN DEFAULT false,
                                  published BOOLEAN DEFAULT true,
                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                  created_by TEXT,
                                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                  updated_by TEXT,
                                  is_deleted BOOLEAN NOT NULL DEFAULT false,
                                  deleted_at TIMESTAMP WITH TIME ZONE,
                                  deleted_by TEXT
);

-- 2. INSCRIPTIONS
CREATE TABLE public.registrations (
                                      id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                      bootcamp_id UUID REFERENCES public.bootcamps(id) ON DELETE SET NULL,
                                      bootcamp_title TEXT,
                                      first_name TEXT NOT NULL,
                                      last_name TEXT NOT NULL,
                                      email TEXT NOT NULL,
                                      phone TEXT,
                                      company TEXT,
                                      position TEXT,
                                      message TEXT,
                                      status TEXT NOT NULL DEFAULT 'pending',
                                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                      created_by TEXT,
                                      updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                      updated_by TEXT,
                                      is_deleted BOOLEAN NOT NULL DEFAULT false,
                                      deleted_at TIMESTAMP WITH TIME ZONE,
                                      deleted_by TEXT
);

-- 3. ALUMNI (Anciens élèves)
CREATE TABLE public.alumni (
                               id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                               registration_id UUID REFERENCES public.registrations(id) ON DELETE SET NULL,
                               bootcamp_id UUID REFERENCES public.bootcamps(id) ON DELETE SET NULL,
                               name TEXT NOT NULL,
                               email TEXT,
                               phone TEXT,
                               current_title TEXT,
                               current_position TEXT,
                               linkedin_url TEXT,
                               photo_url TEXT,
                               cohort TEXT,
                               year INTEGER,
                               published BOOLEAN DEFAULT true,
                               display_order INTEGER DEFAULT 0,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                               created_by TEXT,
                               updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                               updated_by TEXT,
                               is_deleted BOOLEAN NOT NULL DEFAULT false,
                               deleted_at TIMESTAMP WITH TIME ZONE,
                               deleted_by TEXT
);

-- 4. PROJETS (Réalisations des bootcamps)
CREATE TABLE public.projects (
                                 id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                 bootcamp_id UUID REFERENCES public.bootcamps(id) ON DELETE SET NULL,
                                 title TEXT NOT NULL,
                                 description TEXT,
                                 tools_technologies TEXT[] DEFAULT '{}',
                                 access_link TEXT,
                                 cover_image_url TEXT,
                                 cohort TEXT,
                                 year INTEGER,
                                 published BOOLEAN DEFAULT true,
                                 display_order INTEGER DEFAULT 0,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 created_by TEXT,
                                 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 updated_by TEXT,
                                 is_deleted BOOLEAN NOT NULL DEFAULT false,
                                 deleted_at TIMESTAMP WITH TIME ZONE,
                                 deleted_by TEXT
);

-- 5. MEMBRES DU PROJET (Table de jointure)
CREATE TABLE public.project_members (
                                        id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                        project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
                                        alumni_id UUID NOT NULL REFERENCES public.alumni(id) ON DELETE CASCADE,
                                        role TEXT,
                                        display_order INTEGER DEFAULT 0,
                                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                        created_by TEXT
);

-- 6. CAPTURES D'ÉCRAN PROJET
CREATE TABLE public.project_screenshots (
                                            id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                            project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
                                            photo_url TEXT NOT NULL,
                                            caption TEXT,
                                            display_order INTEGER DEFAULT 0,
                                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                            created_by TEXT
);

-- 7. SERVICES, RÉFÉRENCES, TÉMOIGNAGES, GALERIE (Simplifiés)
CREATE TABLE public.services (
                                 id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                 title TEXT NOT NULL,
                                 description TEXT,
                                 icon_name TEXT,
                                 features TEXT[],
                                 duration TEXT,
                                 display_order INTEGER DEFAULT 0,
                                 published BOOLEAN DEFAULT true,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE public.references (
                                   id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                   name TEXT NOT NULL,
                                   full_name TEXT,
                                   category TEXT NOT NULL DEFAULT 'client',
                                   sector TEXT,
                                   logo_url TEXT,
                                   logo_text TEXT,
                                   display_order INTEGER DEFAULT 0,
                                   published BOOLEAN DEFAULT true,
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                   updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                   is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE public.testimonials (
                                     id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                     name TEXT NOT NULL,
                                     role TEXT,
                                     company TEXT,
                                     content TEXT NOT NULL,
                                     rating INTEGER DEFAULT 5,
                                     published BOOLEAN DEFAULT true,
                                     display_order INTEGER DEFAULT 0,
                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                     updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                     is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE public.gallery_photos (
                                       id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                       url TEXT NOT NULL,
                                       caption TEXT,
                                       bootcamp_name TEXT,
                                       display_order INTEGER DEFAULT 0,
                                       published BOOLEAN DEFAULT true,
                                       created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                       updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                       is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE public.contact_messages (
                                         id UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
                                         first_name TEXT NOT NULL,
                                         last_name TEXT NOT NULL,
                                         email TEXT NOT NULL,
                                         phone TEXT,
                                         company TEXT,
                                         subject TEXT,
                                         message TEXT NOT NULL,
                                         status TEXT NOT NULL DEFAULT 'unread',
                                         notes TEXT,
                                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                         updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                         is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- ============================================================
-- TRIGGERS POUR UPDATED_AT
-- ============================================================

CREATE OR REPLACE FUNCTION public.update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_bootcamps_upd BEFORE UPDATE ON public.bootcamps FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_registrations_upd BEFORE UPDATE ON public.registrations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_alumni_upd BEFORE UPDATE ON public.alumni FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_projects_upd BEFORE UPDATE ON public.projects FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_services_upd BEFORE UPDATE ON public.services FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_references_upd BEFORE UPDATE ON public.references FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_testimonials_upd BEFORE UPDATE ON public.testimonials FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_gallery_upd BEFORE UPDATE ON public.gallery_photos FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER tr_messages_upd BEFORE UPDATE ON public.contact_messages FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();