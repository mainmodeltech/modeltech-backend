# Refonte Model Technologie — dossier de passation design → développement

Source de vérité visuelle : le canevas « Refonte site Model Technologie » (claude.ai, 2 pages : *Site public* et *Plateforme — apprenants & admin*).
Ce dossier contient la même chose sous forme lisible par Claude Code : les sources HTML de chaque écran (`screens/`) et cette spécification.

> Les fichiers `screens/*.dc.html` sont des **références visuelles**, pas du code à copier : du HTML avec styles inline, hors React. Il faut les **ré-implémenter** avec la stack existante (React 18 + Vite + TS + Tailwind + shadcn/ui côté front ; Spring Boot 3.4 + PostgreSQL + Flyway + MinIO côté back), en respectant les conventions des `CLAUDE.md` des deux repos.
> Tout texte entre `[crochets]` est un contenu à fournir par Lionnel (dates, prix, taux, noms) : ne rien inventer, garder un placeholder ou le rendre configurable depuis l'admin.

---

## 1. Design system (tokens)

| Rôle | Valeur |
|---|---|
| Fond site (ivoire) | `#FAF7F2` |
| Fond app | `#F6F3EE` |
| Surface / cartes | `#FFFFFF`, bordure `#E7E0D4` |
| Surface sable (encarts) | `#F1ECE3` |
| Marine (marque, titres, sidebar) | `#1A3A5B` — foncé `#10263C`, moyen `#234A70` |
| Accent (CTA) | `#C73A08` (orange du logo `#E6440A` assombri pour contraste AA sur blanc) |
| Accent clair sur fond marine | `#FFB08A` |
| Texte | `#16202E` ; secondaire `#4A5566` |
| Succès | `#1F6B4F` / fond `#E3F2EA` |
| Attention | `#8A5A00` / fond `#FDF1D8` |
| Erreur / nouveau | `#9A2E06` / fond `#FDE8DF` |
| Info | fond `#E6EEF6` |
| LinkedIn (bouton seulement) | `#0A66C2` |

- Polices : **Bricolage Grotesque** (titres, 700–800) + **DM Sans** (texte, 400–700), via Google Fonts. Remplacent Poppins/Inter actuels.
- Rayons : 999px (boutons pilule du site), 12–14px (boutons app), 18–28px (cartes).
- Titres : letter-spacing négatif (-1 à -2.5px) sur les grands titres.
- Accessibilité : cibles ≥ 44px, vrais `<button>`/`<a>`/`<label>`, contraste AA.
- Mobile : barre d'onglets en bas, PWA installable.

À faire en premier : mapper ces tokens dans `tailwind.config.ts` + variables CSS shadcn (`--primary`, etc.) dans `index.css`.

---

## 2. Inventaire des écrans → routes

### Site public (`screens/site/`)
| Fichier | Route proposée | Notes |
|---|---|---|
| `Main.dc.html` | `/` | Remplace `Index.tsx`. Hero, bloc cohorte, par métier, parcours Data & BI, bandeau Gestion de projet, coaching, témoignages, entreprises, FAQ |
| `Mobile.dc.html` | `/` (responsive) | Référence mobile du haut de l'accueil |
| `Formations.dc.html` | `/formations` | **Catalogue par domaines** + filtres + calendrier. Remplace `Bootcamps.tsx` / `Bootcampsessions.tsx` |
| `FicheFormation.dc.html` | `/formations/:slug` | **Gabarit unique** interne/partenaire ; seul le bloc « Dispensé par » change |
| `Coaching.dc.html` | `/coaching` | Formules, créneaux, réservation |
| `Entreprises.dc.html` | `/entreprises` | Offres B2B + formulaire diagnostic |
| `Alumni.dc.html` | `/alumni` | Remplace `Alumni.tsx` |
| `Ressources.dc.html` | `/ressources` | Blog, téléchargements, quiz d'orientation (existe : `Orientation.tsx`) |
| `Contact.dc.html` | `/contact` | Remplace `Contact.tsx` |
| `Partenaires.dc.html` | `/partenaires` | Nouveau : devenir partenaire formateur |

### Plateforme (`screens/app/`)
| Fichier | Route proposée | Rôle |
|---|---|---|
| `App-Connexion.dc.html`, `M-Connexion.dc.html` | `/connexion` | Tous (redirige selon le rôle) |
| `App-Apprenant.dc.html`, `M-Accueil.dc.html` | `/espace` | LEARNER |
| `App-Cours.dc.html`, `M-Cours.dc.html`, `M-Programme.dc.html` | `/espace/formations/:id/lecons/:lessonId` | LEARNER |
| `App-Evaluations.dc.html`, `M-Quiz.dc.html` | `/espace/formations/:id/evaluations` | LEARNER |
| `App-Certificats.dc.html`, `M-Certificat.dc.html` | `/espace/certificats` | LEARNER |
| `App-Verification.dc.html` | `/certificats/:publicId` | **Public**, sans auth |
| `App-Email.dc.html` | — | Gabarit e-mail (Brevo) « certificat prêt » |
| `App-Admin-Dashboard.dc.html` | `/admin` | ADMIN (remplace `AdminDashboard.tsx`) |
| `App-Admin-Formation.dc.html` | `/admin/formations/:id` | ADMIN / TRAINER / PARTNER (ses formations) |
| `App-Admin-Session.dc.html` | `/admin/sessions/:id` | ADMIN / TRAINER |
| `App-Admin-Candidatures.dc.html` | `/admin/candidatures` | ADMIN (évolution de `AdminInscriptions.tsx`) |

---

## 3. Modèle de données (backend `modeltech-backend`)

Existant : `Bootcamp`, `BootcampSession`, `Registration` (PENDING/CONFIRMED/CANCELLED/COMPLETED), `PromoCode`, `Alumni`, `Testimonial`, `AdminUser`, `Role` (SUPER_ADMIN, ADMIN, EDITOR).

À ajouter (migrations Flyway à partir de V17) :
- `Domain` (nom, slug, ordre, visible) ; `Partner` (nom, logo, bio, part de revenu %, contact)
- `Bootcamp` → ajouter `domain_id`, `delivered_by` (INTERNAL | PARTNER) + `partner_id`, `level`, `format`, `certification_prep` (ex. PL-300, PSM I)
- `Learner` (compte apprenant distinct d'`AdminUser`) ; rôles `LEARNER`, `TRAINER`, `PARTNER`
- `Enrollment` (learner ↔ session, statut, date d'accès, fin d'accès) — lien avec `Registration` existant
- `Payment` (montant, moyen WAVE | ORANGE_MONEY | VIREMENT | ENTREPRISE, référence, statut, échéances)
- `Module` → `Lesson` (type VIDEO | RESOURCE | QUIZ | LIVE, ordre, durée, statut publication) ; `LessonResource` (fichier MinIO)
- `LessonProgress` (learner, lesson, terminée, position vidéo)
- `Quiz`, `Question`, `Choice`, `QuizAttempt` (score, tentative n°)
- `LiveSession` + `Attendance`
- `ProjectSubmission` (fichiers MinIO, statut, feedback formateur)
- `CertificateRule` (par formation : % leçons, quiz ≥ x %, présence ≥ x %, projet validé)
- `Certificate` (`public_id` type `MT-2026-VBA-00042`, learner, formation, date, PDF MinIO, statut VALID | REVOKED)

Règles clés :
- Certificat généré automatiquement quand toutes les règles sont remplies → PDF → e-mail (Brevo) + WhatsApp (WATI) avec lien `/certificats/:publicId`.
- Endpoint public `GET /public/certificates/{publicId}` (sans auth) pour la page de vérification.
- Bouton LinkedIn : URL `https://www.linkedin.com/profile/add?startTask=CERTIFICATION_NAME&name=…&organizationId=…&issueYear=…&issueMonth=…&certUrl=…&certId=…` (nécessite la page entreprise LinkedIn de Model Technologie pour `organizationId`).
- Vidéos : **pas** servies brutes depuis MinIO → hébergeur vidéo avec streaming adaptatif (HLS) et URLs signées ; `Lesson` stocke l'ID fournisseur.

---

## 4. Plan de branches (depuis `develop`, convention `feature/*` déjà utilisée)

Chaque lot = 1 branche par repo concerné, PR vers `develop`. Ordre conseillé :

| Lot | Front (`data-mastery-hub`) | Back (`modeltech-backend`) | Dépend de |
|---|---|---|---|
| 0 | `feature/design-system-v2` — tokens, polices, composants partagés (Button, Pill, ProgressBar, Card, layouts site/app) | — | — |
| 1 | `feature/site-catalogue-domaines` — `/formations` + `/formations/:slug` | `feature/domain-partner-model` — Domain, Partner, champs formation | 0 |
| 2 | `feature/site-refonte-pages` — accueil, coaching, entreprises, alumni, ressources, contact, partenaires | (formulaires : endpoints existants + `diagnostic`, `partner-application`) | 0, 1 |
| 3 | `feature/learner-auth` — `/connexion`, garde de routes par rôle | `feature/learner-accounts` — Learner, rôles, JWT apprenant, Google OAuth | — |
| 4 | `feature/admin-candidatures-paiements` | `feature/enrollment-payment` | 3 |
| 5 | `feature/admin-formation-editor` + `feature/learner-course-player` + `feature/learner-dashboard` | `feature/course-content` — Module, Lesson, progression, vidéo | 3, 4 |
| 6 | `feature/learner-evaluations` + `feature/admin-session-suivi` | `feature/quiz-project-live` | 5 |
| 7 | `feature/certificates-ui` — espace certificats, page publique, LinkedIn | `feature/certificates` — règles, PDF, e-mail/WhatsApp, endpoint public | 6 |
| 8 | `feature/admin-dashboard-v2` + `feature/pwa-mobile` | `feature/admin-stats` | 4–7 |

⚠️ Des branches existent déjà (`feature/redisign`, `feature/refonte-index`, `feature/payment-frontend-dashboard`, `feature/auth`…). Vérifier avec Lionnel lesquelles sont à reprendre, fusionner ou abandonner avant d'en créer de nouvelles.

---

## 5. Consignes pour Claude Code

1. Lire le `CLAUDE.md` du repo, puis ce README et les écrans du lot en cours.
2. Réutiliser `components/ui` (shadcn) et les services/hooks existants (1 service + 1 hook React Query par entité).
3. Ne jamais coder en dur les contenus métier (prix, dates, places, partenaires) : ils viennent de l'API/admin.
4. Une PR par lot, petite et testable ; captures d'écran avant/après dans la PR.
5. Déploiement : Dokploy (voir les gotchas du projet : variables Vite au build, pas de labels Traefik manuels).
