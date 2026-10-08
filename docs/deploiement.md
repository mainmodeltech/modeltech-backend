# Mise en production — liste de contrôle

Couvre les lots livrés depuis `feature/redisign` : catalogue, formulaires, comptes apprenants, paiements,
contenus du site, programme des formations, évaluations, e-mails. Les migrations Flyway vont de **V18 à V25**
(additives : on ne modifie jamais une migration appliquée).

## 1. Ordre de fusion

`feature/redisign` → `feature/domain-partner-model` → `feature/site-forms` → `feature/learner-accounts` →
`feature/enrollment-payment` → `feature/site-content` → `feature/course-content` →
`feature/quiz-project-live` → `feature/notifications-reliability` → (lot de durcissement). Les branches sont empilées : chaque PR
cible la précédente tant qu'elle n'est pas fusionnée, puis `develop`.

## 2. Avant le déploiement

1. **Sauvegarde** : `pg_dump -Fc -h <hôte> -U <utilisateur> <base> > avant-v25.dump`.
2. **Répétition sur une copie de la production** (indispensable pour V20) :
   ```bash
   createdb copie_prod && pg_restore -d copie_prod avant-v25.dump
   DB_URL=jdbc:postgresql://localhost:5432/copie_prod ... java -jar app.jar     # Flyway applique V18 → V25
   ```
3. **Contrôles SQL sur la copie, après migration** :
   ```sql
   -- V20 : aucun administrateur sans rôle, et au moins un SUPER_ADMIN actif
   SELECT u.email, array_agg(r.name) AS roles FROM admin_users u
     LEFT JOIN admin_user_roles x ON x.admin_user_id = u.id LEFT JOIN roles r ON r.id = x.role_id
    WHERE u.is_deleted = false GROUP BY u.email;
   -- V18 : toutes les formations ont un slug et un domaine
   SELECT count(*) FILTER (WHERE slug IS NULL) AS sans_slug, count(*) FILTER (WHERE domain_id IS NULL) AS sans_domaine FROM bootcamps WHERE is_deleted = false;
   -- V21 : les inscriptions existantes ont gardé leur statut
   SELECT status, count(*) FROM registrations GROUP BY status;
   ```
   Vérifier à la main que chaque administrateur a le rôle attendu : l'amorçage de V20 donne le rôle `ADMIN` à ceux qui n'en avaient aucun
   et `SUPER_ADMIN` au plus ancien compte actif **uniquement si personne ne l'est** — à corriger ensuite depuis `/admin/comptes`.
4. **Prix numériques** : les formations existantes n'ont pas de `price_amount` (jamais déduit du texte « 150 000 FCFA »).
   Sans lui, « Accepter » une candidature exige un `totalAmount`. Renseigner `priceAmount` (et `priceOverrideAmount` / `earlyBirdAmount` des sessions) depuis le back-office.

## 3. Variables d'environnement

| Variable | Obligatoire | Rôle |
|---|---|---|
| `JWT_SECRET` | oui | **64 octets minimum** (HS512). Plus court : le démarrage journalise une erreur et aucune connexion ne fonctionne. `openssl rand -base64 64` |
| `APP_FRONTEND_URL` | **oui** | URL publique du site (ex. `https://www.model-technologie.com`) : base des liens des e-mails (paiement, définition de mot de passe). Défaut `http://localhost:5173` → liens inutilisables en production |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | oui | Compte SMTP (Gmail : mot de passe d'application). Vides : aucun e-mail ne part (tracé `SKIPPED`) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | non | Défaut `smtp.gmail.com:587` ; `MAIL_FROM` vide = le compte SMTP |
| `MAIL_REDIRECT_TO` | **jamais en production** | Redirige tous les e-mails vers une adresse de recette (staging) |
| `NOTIFICATION_EMAIL_TO`, `SLACK_WEBHOOK_URL`, `SLACK_BOOTCAMP_SUBSCRIPTION_WEBHOOK_URL` | selon besoin | Notifications internes |
| `RECAPTCHA_SECRET_KEY` | oui | Désormais **appliqué** à l'inscription (403 si échec) ; le front doit envoyer `recaptchaToken` |
| `CORS_ALLOWED_ORIGINS` | oui (prod) | Origines du front |
| `MINIO_ENDPOINT`, `MINIO_PUBLIC_URL`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_BUCKET` | oui | Stockage (logos, photos, captures de paiement, rendus de projet) |
| `APP_TIMEZONE` | non | Défaut `Africa/Dakar` (dates des lives, accès aux cours) |
| `APP_RATE_LIMIT_LOGIN_PER_HOUR`, `APP_RATE_LIMIT_FORMS_PER_HOUR` | non | Défauts 30 et 10 par heure et par IP (dernier saut de `X-Forwarded-For`) |
| `APP_INVOICE_SELLER_NAME`, `_ADDRESS`, `_PHONE`, `_EMAIL`, `_WEBSITE`, `_TAX_ID` (NINEA), `_REGISTER_NUMBER` (RCCM), `_PAYMENT_DETAILS`, `_FOOTER`, `APP_INVOICE_VAT_PERCENT` | recommandées | Mentions légales imprimées sur les factures (vides = non imprimées) |
| `APP_CERTIFICATE_SIGNATORY_NAME`, `_SIGNATORY_TITLE`, `APP_CERTIFICATE_LINKEDIN_ORGANIZATION_ID` | non | Défauts : Patrick Lionnel DOOKO, « Gérant, Model Technologie », 103600105 |
| `GOOGLE_CLIENT_ID` | non | ID client OAuth (Web) de la connexion Google ; vide = bouton Google masqué |
| `AUTH_LOCKOUT_MAX_ATTEMPTS` / `AUTH_LOCKOUT_MINUTES` / `AUTH_PASSWORDLESS_MINUTES` | non | Verrouillage après échecs (5 / 15 min) et validité du lien-code (10 min) |
| `APP_MESSAGING_CHANNELS` | non | Canaux de diffusion (défaut `EMAIL`) ; `APP_MESSAGING_LIVE_REMINDER_CRON` pour l'horaire des rappels |
| `APP_PAYMENT_*`, `APP_COURSE_DEFAULT_*` | non | Délais de paiement / relances, règles de certificat par défaut (voir `CLAUDE.md`) |

Le profil `prod` coupe Swagger et passe les logs applicatifs en `INFO`.

## 4. Après le déploiement (tests de fumée)

1. `GET /actuator/health` → `UP` (inclut la connexion SMTP).
2. Connexion d'un administrateur ; `GET /api/v1/auth/me` renvoie ses `roles`.
3. Se connecter comme SUPER_ADMIN → `POST /api/v1/admin/email-logs/test` vers votre adresse → e-mail reçu ; `GET /api/v1/admin/email-logs?status=FAILED` vide.
4. Site public : `/formations`, fiche formation, formulaire d'inscription (reCAPTCHA), contact.
5. Parcours complet sur une formation de test : candidature → accepter → lien de paiement reçu → déclarer → confirmer → e-mail d'invitation → connexion apprenant.

## 5. Retour arrière

Les migrations sont additives : repasser l'application à la version précédente ne casse pas le schéma. **Exception** : si des
candidatures ont déjà été acceptées (`PAYMENT_PENDING`, `PAYMENT_TO_CONFIRM`, `REJECTED`), l'ancienne version ne reconnaît pas ces
statuts ; les ramener d'abord à `PENDING` / `CANCELLED`. Le plus sûr reste la restauration de la sauvegarde si la bascule échoue dans les premières minutes.

## 6. Points d'attention connus

- Le journal d'e-mails ne contient jamais le corps des messages (certains portent un lien de mot de passe).
- Les rendus de projet sont stockés sous une clé aléatoire ; seul un lien signé de 15 minutes permet de les télécharger. Les captures de paiement, elles, sont dans le bucket public sous une clé aléatoire.
- La limitation de débit est en mémoire (une instance). À remplacer par un stockage partagé si le backend est répliqué.
- `docker-compose` de staging / production : l'image MinIO `minio/minio` n'est plus tirable depuis Docker Hub ; épingler une image disponible avant le prochain déploiement.

### Passer à Brevo (fournisseur d'e-mails transactionnels)

Aucun code à changer : Brevo expose un relais SMTP, il suffit de trois variables.

1. Créer un compte Brevo, puis **Expéditeurs, domaines** : ajouter `model-technologie.com` et publier chez le registrar les enregistrements **SPF, DKIM et DMARC** indiqués (sans eux, les messages finissent en spam).
2. **SMTP & API** : créer une **clé SMTP** (≠ clé API) ; noter l'identifiant SMTP.
3. Variables : `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=587`, `MAIL_USERNAME=<identifiant SMTP>`, `MAIL_PASSWORD=<clé SMTP>`, `MAIL_FROM=noreply@model-technologie.com` (adresse d'un domaine validé).
4. Contrôle : `POST /api/v1/admin/email-logs/test` (SUPER_ADMIN) avec une adresse de test, puis `GET /api/v1/admin/email-logs?status=FAILED`.

