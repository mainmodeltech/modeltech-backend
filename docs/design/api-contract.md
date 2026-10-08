# Contrat d'API — alignement backend ↔ frontend (lots front 0 à 4)

> **Statut : ÉTAPE 1 (audit) — en attente de validation. Rien n'est implémenté.**
> Date de l'audit : 2026-10-02.

## 0. Périmètre et sources auditées

| Dépôt | Branche / commit audité | Remarque |
|---|---|---|
| `modeltech-backend` | `develop` @ `6f5b79b` (propre) | Migrations Flyway jusqu'à **V16**. |
| `modeltech-backend` | `feature/redisign` @ `1a59a69` — **poussée, NON fusionnée** | Contient V17 (contenu riche des bootcamps, FK témoignage → bootcamp, `schedule`) et le correctif `SecurityConfig` des routes `/bootcamps/{id}`. Voir Q1. |
| `data-mastery-hub` (front) | `feature/admin-candidatures-paiements` @ `659f1c3` | Lots 0 → 4 empilés. Lecture seule. |

Méthode : lecture de `src/services/**`, `src/types/**`, `src/hooks/**`, `src/pages/**`, `src/config/mockFormationsCatalog.ts`, `src/lib/roles.ts`, `App.tsx`, puis comparaison avec tous les controllers, DTO, entités, migrations et `SecurityConfig` du backend, et avec les écrans `docs/design/screens/**`.

**Deux `CLAUDE.md` sont en retard sur le code** et ne doivent pas servir de référence : celui du front ne connaît ni `formation.type.ts`, ni `formationService.ts`, ni `roles.ts` ; celui du back s'arrête à V10 (on est à V16), décrit un `SecurityConfig` et des enums qui n'existent plus tels quels (`RegistrantProfile` → en réalité `RegistrationProfile`, dans `entity/`), et liste `GET /services` comme public alors que la config ne le permet pas (§10). Je propose de les remettre à jour dans la PR du lot a.

Légende de la colonne « Écart » : **OK** · **À CRÉER** · **À MODIFIER** · **CHAMP MANQUANT** · **NOM DIFFÉRENT**.

---

## 1. Constats principaux (à lire en premier)

| # | Constat | Gravité | Lot |
|---|---|---|---|
| C1 | Les endpoints `/formations`, `/formations/slug/{slug}`, `/formations/sessions`, `/domains`, `/partners` **n'existent pas**. Le front bascule silencieusement sur `mockFormationsCatalog.ts` (`catch → MOCK`). Une API qui répondrait `[]` afficherait un catalogue **vide** (pas de repli). | Bloquant | a |
| C2 | **Le JWT ne contient pas de claim `roles`** (`JwtTokenProvider` : `sub`, `iat`, `exp` seulement). Le front lit `roles` dans le JWT (`useAuth.tsx`, `roles.ts`) et traite un jeton **sans rôle comme du personnel** (`hasAnyRole` : « jetons antérieurs… »). Un futur apprenant serait donc vu comme staff côté UI. | Bloquant | c |
| C3 | **Aucun contrôle de rôle sur `/api/v1/admin/**`** : `anyRequest().authenticated()` + `@PreAuthorize("isAuthenticated()")` sur quelques contrôleurs (et `@PreAuthorize` commenté sur `AdminContactMessageController`). Dès qu'un `Learner` pourra se connecter, il pourra appeler toute l'API admin. À fermer **avant** d'ouvrir le login apprenant. | Sécurité critique | c |
| C4 | **Risque de verrouillage des admins** si on impose `hasAnyRole(...)` : aucune migration ne remplit `admin_user_roles` (V13 crée les tables et 3 rôles, c'est tout). Les rôles des admins existants ont été posés à la main ; leur état en staging/prod est inconnu. Il faut une migration de rattrapage (et la vérifier sur une copie de prod) avant d'activer l'enforcement. | Régression possible | c |
| C5 | `UserDetailsServiceImpl` et `JwtAuthenticationFilter` ne chargent que `admin_users`. Un jeton `Learner` provoquerait un `UsernameNotFoundException` dans le filtre. | Bloquant | c |
| C6 | **Les prix sont des chaînes** (`"150 000 FCFA"`, `price`, `priceOverride`, `earlyBirdPrice`) : impossible de calculer un montant, une remise promo ou des échéances. Le lot d exige des montants numériques. | Bloquant | d |
| C7 | `feature/redisign` (V17) n'est pas fusionnée dans `develop` alors que le README prévoit « migrations à partir de V17 » et que le contrat `Formation` du front consomme déjà `profiles/tools/curriculum/outcomes/testimonial/certification`. Collision de numérotation et dépendance fonctionnelle. | Décision requise | a |
| C8 | **Aucun test dans le dépôt** (`src/test` absent ; seul `spring-boot-starter-test` est déclaré). La règle « un test d'intégration par controller » impose d'introduire l'infrastructure (voir §7). `jsonb` et `text[]` imposent PostgreSQL réel (pas H2). | Cadrage | tous |
| C9 | Les formulaires « diagnostic entreprise » et « candidature partenaire » passent aujourd'hui par `POST /contact-messages` avec les champs **encodés dans la chaîne `message`** ; la newsletter (`Ressources.tsx`) **n'appelle aucun endpoint** ; `Coaching` renvoie vers `/contact?sujet=coaching` que `Contact.tsx` ignore. | À décider | b |
| C10 | `RegistrationResponse` **n'expose pas** `country`, `profile`, `school` alors que le type front `Registration` les déclare ; la liste admin est triée par `createdAt` **croissant** par défaut alors que `AdminCandidatures` charge « les 100 plus récentes ». | À modifier | d |

---

## 2. Conventions d'enveloppe observées

Le back n'est pas homogène (le `CLAUDE.md` impose `ApiResponse<T>` partout, la réalité est mixte) et le front s'est adapté endpoint par endpoint :

| Famille | Enveloppe réelle côté back | Ce que le front attend | Verdict |
|---|---|---|---|
| Auth (`/auth/*`) | brut (`AuthResponse`, `MessageResponse`) | brut | OK |
| Bootcamps public / admin (liste) | `List<…>` brut | brut (`bootcampService`, `adminBootcampService`) | OK |
| Registrations / promo / services admin | `Page<…>` Spring brut (`content`, `totalElements`…) | `PaginatedResponse<T>` | OK |
| Contact, témoignages, masterclass | `ApiResponse<T>` (+ `pagination`) | `ApiResponse<T>` | OK |
| Alumni / projets / services public | `List<…>` brut | brut | OK |

**Règle proposée pour les nouveaux endpoints** (à valider, Q2) :
1. endpoint dont le consommateur front **existe déjà** (catalogue `/formations`, `/domains`, `/partners`) → on suit le front : **JSON brut** ;
2. endpoint **sans consommateur front** (paiements, apprenants, formulaires typés) → `ApiResponse<T>` (convention backend), le front s'adaptera ;
3. les endpoints existants **ne changent pas d'enveloppe**.

Erreurs : `ErrorResponse { timestamp, status, error, message, path, validationErrors }` — le front lit uniquement `message`. Le `CLAUDE.md` front parle de `errors` : obsolète.

---

## 3. Tableau endpoint par endpoint

Préfixe de tous les chemins : `/api/v1`. « Front » = `data-mastery-hub` (fichier entre parenthèses).

### 3.A Authentification

| # | Méthode | Chemin | Attendu par le front | Existant au back | Écart |
|---|---|---|---|---|---|
| A1 | POST | `/auth/login` | `{email,password}` → `{accessToken,tokenType,expiresIn}` ; **décode le JWT** : `sub`, `roles[]`, `exp` (`useAuth.tsx`) | Existe. JWT **sans `roles`**. Ne connaît que `admin_users`. | **À MODIFIER** : claim `roles`, login apprenant (lot c) |
| A2 | GET | `/auth/me` | `AuthUser {id,email,fullName,role}` (`authService.ts`, **jamais appelé** par `useAuth`) | `{id,email,fullName,primaryRole,roles[]}` | **NOM DIFFÉRENT** (`role` vs `primaryRole`) — sans impact aujourd'hui |
| A3 | POST | `/auth/logout` | défini dans `authService.logout()` mais `signOut()` **ne l'appelle pas** | Existe (blacklist SHA-256) | OK back · front à corriger (token jamais révoqué) |
| A4 | POST | `/auth/forgot-password` | `{email}` — `AdminForgotPassword.tsx` fait `const { forgotPassword } = useAuth()` or **`useAuth` n'expose plus cette fonction** (contexte = `user, token, loading, signIn, signOut`) : la page lève une `TypeError` à la soumission ; `authService.forgotPassword` existe mais n'est utilisé nulle part | Existe, admins uniquement ; lien généré `…/admin/reset-password?token=` en dur | **À MODIFIER** (apprenants ; chemin neutre, Q17) · **front cassé** (§9) |
| A5 | POST | `/auth/reset-password` | `{token,newPassword}` — même problème dans `AdminResetPassword.tsx` (`useAuth().resetPassword` inexistant) | Existe, admins uniquement | **À MODIFIER** (apprenants) · **front cassé** (§9) |
| A6 | PUT | `/auth/change-password` | — | Existe | OK |
| A7 | — | Google OAuth, lien magique e-mail, code WhatsApp | `ComingSoonLink` — **aucun appel** | Absent | **Hors périmètre** (consigne : seulement si le front les appelle) |

### 3.A bis — Lot c livré (`feature/learner-accounts`) : contrats exacts à utiliser côté front

**Auth (réponses brutes, inchangées dans leur forme)**

- `POST /auth/login` → `{accessToken, tokenType, expiresIn, user:{id,email,fullName,primaryRole,roles[],userType,partnerId?}}`. Le **même** endpoint connecte admins et apprenants. `userType` = `ADMIN` | `LEARNER`. Le JWT porte `sub`, `roles[]` (noms stockés : `ROLE_ADMIN`, `ROLE_LEARNER`…), `uty`, `exp`. 401 si identifiants invalides, compte désactivé ou mot de passe pas encore défini.
- `POST /auth/forgot-password` `{email}` → toujours 200 ; le lien envoyé dépend du type de compte : admin `…/admin/reset-password?token=…`, apprenant `…{app.frontend.learner-reset-path}?token=…` (défaut `/reinitialiser-mot-de-passe`, propriétés `app.frontend.url` / `app.frontend.learner-reset-path`, soit les variables d'environnement `APP_FRONTEND_URL` / `APP_FRONTEND_LEARNER_RESET_PATH` — **`APP_FRONTEND_URL` doit être renseignée en staging/prod** (défaut `http://localhost:5173`)).
- `POST /auth/reset-password` `{token, newPassword(≥8)}` → 200 ; jeton invalide, expiré ou déjà utilisé → **400** « Lien de réinitialisation invalide ou expiré. ». Sert aussi à **définir le premier mot de passe** d'un compte invité.
- `GET /auth/me` → profil (même forme que `user` ci-dessus).

**Admin apprenants** (`ApiResponse`, rôles `SUPER_ADMIN`/`ADMIN`) — `LearnerResponse {id,email,firstName,lastName,phone,country,active,passwordSet,emailVerifiedAt,lastLoginAt,createdAt}`

| Méthode | Chemin | Corps / effet |
|---|---|---|
| GET | `/admin/learners?page&size` | liste paginée |
| GET | `/admin/learners/{id}` | détail ; 404 |
| POST | `/admin/learners` | `{firstName*, lastName, email*, phone, country}` → 201 + e-mail d'invitation (72 h) ; 409 si l'e-mail est déjà un compte (apprenant **ou** back-office) |
| PATCH | `/admin/learners/{id}/activate` · `/deactivate` | la désactivation refuse aussi les jetons déjà émis |
| POST | `/admin/learners/{id}/resend-invitation` | 409 si le mot de passe est déjà défini |

**Admin comptes back-office** (`ApiResponse`, **SUPER_ADMIN uniquement**) — `AdminUserSummaryResponse {id,email,fullName,roles[],active,partnerId,lastLoginAt,createdAt}`

| Méthode | Chemin | Corps / effet |
|---|---|---|
| GET | `/admin/users` · `/admin/users/{id}` | liste paginée · détail |
| POST | `/admin/users` | `{email*, fullName*, roles*[SUPER_ADMIN\|ADMIN\|EDITOR\|TRAINER\|PARTNER], partnerId}` → 201 + invitation ; `PARTNER` ⇒ `partnerId` obligatoire (400) ; `LEARNER`/inconnu ⇒ 400 ; e-mail pris ⇒ 409 |
| PUT | `/admin/users/{id}` | champs optionnels `{fullName, roles, partnerId, active}` ; 409 si l'on se désactive / se retire SUPER_ADMIN, ou si l'on retirerait le dernier SUPER_ADMIN actif |

**Sécurité** : matrice §6 appliquée (`EDITOR` n'a plus accès à `/admin/registrations` ni `/admin/promo-codes`, défaut Q22). Un JWT apprenant reçoit 403 sur tout `/admin/**`.

**Migration V20** : crée `learners`/`learner_roles`, les rôles LEARNER/TRAINER/PARTNER, `admin_users.partner_id`, **rattrape les rôles des admins existants** (colonne historique `admin_users.role` → `ROLE_<ROLE>`, sinon `ROLE_ADMIN`) et **promeut le plus ancien admin actif SUPER_ADMIN si aucun n'existe** (sans quoi personne ne pourrait gérer les comptes). ⚠️ À contrôler sur une copie de la base de prod avant déploiement : `SELECT u.email, array_agg(r.name) FROM admin_users u LEFT JOIN admin_user_roles x ON x.admin_user_id=u.id LEFT JOIN roles r ON r.id=x.role_id GROUP BY u.email;`

**À faire côté front pour ce lot** (cf. §9) : page « définir mon mot de passe » apprenant (route = `app.frontend.learner-reset-path`), lire `userType`/`roles` pour router apprenant vs back-office, pages Admin « Apprenants » et « Comptes » (§9 point 14), corriger `AdminForgotPassword`/`AdminResetPassword` (§9 point 0).

### 3.B Catalogue (lot a)

| # | Méthode | Chemin | Attendu par le front | Existant au back | Écart |
|---|---|---|---|---|---|
| B1 | GET | `/formations` (skipAuth) | `Formation[]` brut (`formationService.list`) ; filtres faits **côté client** aujourd'hui | Absent | **À CRÉER**. Paramètres de filtre optionnels : `domain` (slug), `level`, `format`, `deliveredBy`, `targetRole` |
| B2 | GET | `/formations/slug/{slug}` (skipAuth) | `Formation` (404 → page « introuvable ») | Absent | **À CRÉER** |
| B3 | GET | `/formations/sessions` (skipAuth) | `FlatSession[]` triée par `startDate` (vue calendrier) | Absent | **À CRÉER** (Q8 : quels statuts) |
| B4 | GET | `/domains` (skipAuth) | `Domain[]` | Absent | **À CRÉER** |
| B5 | GET | `/partners` (skipAuth) | `Partner[]` | Absent | **À CRÉER** |
| B6 | GET | `/bootcamps` | `Bootcamp[]` avec `nextSession` (Header, `/bootcamps`, `SessionsSection`) | Existe | **OK — à conserver strictement** (la prod et plusieurs pages en dépendent). Champ `schedule` : **CHAMP MANQUANT** sur `develop` (présent sur `feature/redisign`) |
| B7 | GET | `/bootcamps/{id}`, `/bootcamps/{id}/sessions` | `bootcampService.findById/findSessions` | Existent mais **403 pour un anonyme** sur `develop` (matcher `SecurityConfig`) | **À MODIFIER** (correctif déjà sur `feature/redisign`) |
| B8 | GET | `/sessions/{id}/availability` | `useSessionAvailability` (résultat ignoré) | Existe | OK |
| B9 | GET/POST/PUT/DELETE/PATCH | `/admin/bootcamps`, `/admin/bootcamps/{id}`, `…/toggle-published`, `…/{id}/sessions`, `…/sessions/{sid}`, `…/sessions/{sid}/toggle-featured` | `adminBootcampService` (liste = tableau brut) | Existent, formes identiques | **OK** pour les champs actuels ; **CHAMP MANQUANT** : `schedule` (le type front `BootcampSession.schedule` est non nul), champs formation (domaine, partenaire, niveau…) |
| B10 | CRUD | `/admin/domains` | Aucun appelant (l'éditeur admin arrive au lot 5) | Absent | **À CRÉER** (proposé, requis par la règle « admin CRUD ») |
| B11 | CRUD (+ logo) | `/admin/partners`, `/admin/partners/{id}/logo` | Aucun appelant | Absent | **À CRÉER** (proposé) |

#### Contrat JSON exact : `Formation` (extrait de `src/types/formation.type.ts`)

`Formation` = `Bootcamp` actuel **+** les champs ci-dessous (noms identiques, camelCase). Même `id` que le bootcamp → `RegistrationModal` continue d'envoyer `bootcampId`.

| Champ JSON | Type | Source back proposée | État |
|---|---|---|---|
| `id,title,description,duration,audience,prerequisites,price,benefits,category,tag,iconName,featured,published,displayOrder,createdAt,updatedAt` | existants | `Bootcamp` | OK |
| `nextSession` | `BootcampSession \| null` | calculé (déjà fait) | OK |
| `sessions?` | `BootcampSession[]` | calculé (déjà fait pour le détail) | OK — **à inclure aussi dans la liste** : `flattenSessions` du front lit `f.sessions ?? [f.nextSession]` |
| `slug` | string unique | `bootcamps.slug` | **À CRÉER** (rétro-remplissage, Q7) |
| `domainId` / `domain` | `string` / `Domain` | `bootcamps.domain_id` → `domains` | **À CRÉER** |
| `deliveredBy` | `"INTERNAL" \| "PARTNER"` | `bootcamps.delivered_by` | **À CRÉER** |
| `partnerId` / `partner` | `string\|null` / `Partner\|null` | `bootcamps.partner_id` → `partners` | **À CRÉER** |
| `level` | `"DEBUTANT" \| "INTERMEDIAIRE" \| "AVANCE"` | `bootcamps.level` | **À CRÉER** |
| `format` | `"PRESENTIEL" \| "EN_LIGNE" \| "HYBRIDE"` | `bootcamps.format` | **À CRÉER** · **NOM DIFFÉRENT** avec `SessionFormat` (`REMOTE`/`HYBRID`) — Q4 |
| `targetRoles` | `string[]` | `bootcamps.target_roles text[]` | **À CRÉER** — le front le marque lui-même « proposition non confirmée » (Q5) |
| `certificationPrep` | `string\|null` | `bootcamps.certification_prep` | **À CRÉER** |
| `profiles, tools, curriculum, outcomes, certification` | listes / objet | colonnes JSONB **déjà écrites sur `feature/redisign`** | **CHAMP MANQUANT sur `develop`** (Q1) |
| `testimonial` | `{name,role,company,content,initials}\|null` | FK `testimonials.bootcamp_id` (redisign) | **CHAMP MANQUANT sur `develop`** (Q1) |
| `tagline`, `colorKey` | string | colonnes redisign | Exposés par le back mais **absents du type front `Formation`** : `FormationDetail` les code en dur (`tagline: formation.title`, `colorKey: "accent"`) → à corriger côté front (§9) |
| `relatedFormationIds` | `string[]` | table de liaison `bootcamp_related` ou calcul « même domaine » | **À CRÉER** (Q6) |

`Domain` : `{id, slug, name, description, badge, comingSoon, displayOrder}` — le README ne prévoyait que « nom, slug, ordre, visible » : **`description`, `badge`, `comingSoon` sont des ajouts nécessaires** (carte « Prochain domaine », badge « NOTRE SPÉCIALITÉ »).
`Partner` (public) : `{id, slug, name, logoUrl, bio, website}`. **La part de revenu (%) et le contact ne sortent jamais sur l'API publique** (DTO public ≠ DTO admin).
`FlatSession` = `BootcampSession` + `{formationId, formationSlug, formationTitle, domainName, deliveredBy, partnerName}`.

### 3.C Inscriptions / candidatures (existant)

| # | Méthode | Chemin | Attendu par le front | Existant au back | Écart |
|---|---|---|---|---|---|
| C1 | POST | `/registrations` | `CreateRegistrationDTO` : `bootcampId,bootcampTitle,sessionId,firstName,lastName,email,phone,profile,company,country,school,position,message,promoCode,recaptchaToken` ; gère 429 et 403 | Existe, tous les champs concordent | **OK** sur le contrat. Mais : le résultat de `recaptchaService.verify()` est **ignoré** (§10) ; pas de limitation de débit (429 jamais émis) ; `ResponseStatusException` (409 « session complète ») probablement renvoyé en 500 (§10) |
| C2 | GET | `/admin/registrations?page&size&status` | `PaginatedResponse<Registration>` ; `AdminCandidatures` charge `size=100` « les plus récentes » | Existe (`Page` brut) | **À MODIFIER** : tri `createdAt DESC` par défaut ; **CHAMP MANQUANT** `country`, `profile`, `school` dans `RegistrationResponse` ; filtres `bootcampId`, `sessionId`, `q` optionnels |
| C3 | GET | `/admin/registrations/{id}` | idem | Existe | OK (+ mêmes champs manquants) |
| C4 | PATCH | `/admin/registrations/{id}/status` | `{status}` avec `PENDING/CONFIRMED/CANCELLED/COMPLETED` ; « Accepter » → `CONFIRMED`, « Refuser » → `CANCELLED`, « Terminé » → `COMPLETED` | Existe ; incrémente/décrémente les places à `CONFIRMED`/`CANCELLED`/`COMPLETED` ; e-mail « place confirmée » à la 1ʳᵉ confirmation | **À MODIFIER** (pipeline paiement, §4 lot d). Contrainte SQL `registrations_status_check` (V7) à recréer |
| C5 | DELETE | `/admin/registrations/{id}` | idem | Existe (soft delete) | OK |
| C6 | POST | `/admin/registrations` (« Inscription manuelle ») | `ComingSoonLink`, aucun appel | Absent | **À CRÉER** (proposé) |
| C7 | GET | `/promo-codes/validate?code=` | lit `discountPercent` et `message?` (`promoCodeService.check`) | Existe → `PromoCodeResponse` complet | OK (`valid` absent mais non lu) |
| C8 | CRUD + PATCH | `/admin/promo-codes`, `…/{id}/toggle` | `promoCodeService` | Existent | OK. (`promoCodeService.validate` appelle `/admin/promo-codes/validate/{code}` : **n'existe pas**, code mort front) |

### 3.D Paiements et inscriptions (lot d) — **tout est à créer** : le front n'a aucun contrat, seulement deux colonnes « en cours » et un lien « Inscription manuelle » désactivés

Le design (`App-Admin-Candidatures.dc.html`) montre : *Nouvelles candidatures* (Accepter / Refuser) → *Paiement en attente* (« lien de paiement envoyé il y a 2 j · relance auto demain » ; « facture entreprise demandée · bon de commande ») → *Paiement à confirmer* (« Wave · 150 000 FCFA · réf. [xxxx] · Confirmer · Voir la preuve ») → *Inscrits* (« compte créé · e-mail de bienvenue », « Payé en 2 fois · 2ᵉ échéance le [date] »). Automatisation décrite : acceptation → lien de paiement (e-mail + WhatsApp) → relance J+2 → paiement confirmé → compte apprenant créé, accès ouvert au démarrage de la session.

| # | Méthode | Chemin (proposé) | Rôle | Notes |
|---|---|---|---|---|
| D1 | POST | `/admin/registrations/{id}/accept` | SUPER_ADMIN, ADMIN | `PENDING → PAYMENT_PENDING` ; crée les `Payment` (1 ou N échéances, montants calculés) ; envoie le lien |
| D2 | POST | `/admin/registrations/{id}/reject` `{reason?}` | idem | `PENDING → REJECTED` |
| D3 | POST | `/admin/registrations/{id}/payments` | idem | enregistrement manuel (virement, espèces, **facture/bon de commande entreprise**) |
| D4 | GET | `/admin/payments?status=&registrationId=&page=` | idem | file « Paiement à confirmer » |
| D5 | POST | `/admin/payments/{id}/confirm` | idem | → `CONFIRMED` ; si toutes échéances requises payées : inscription `CONFIRMED`, création/rattachement du `Learner`, création de l'`Enrollment`, place comptée, e-mail de bienvenue |
| D6 | POST | `/admin/payments/{id}/reject` `{reason}` | idem | preuve invalide → retour `PAYMENT_PENDING` |
| D7 | POST | `/admin/payments/{id}/remind` | idem | relance manuelle (la relance J+2 est un scheduler) |
| D8 | GET | `/payments/{token}` | public (jeton) | page « lien de paiement » : formation, montant, échéances, moyens |
| D9 | POST | `/payments/{token}/declaration` `{method, reference}` | public (jeton) | candidat déclare son paiement → `PAYMENT_TO_CONFIRM` |
| D10 | POST | `/payments/{token}/proof` (multipart) | public (jeton) | preuve → MinIO (même schéma que photos alumni) |
| D11 | GET | `/admin/enrollments`, `/admin/learners` | ADMIN | listes ; **sans appelant front** aujourd'hui (optionnel) |

Chemins publics : le README écrit `/public/...` ; la convention réelle du backend est `PublicXxxController @RequestMapping("/api/v1/<pluriel>")` **sans** préfixe `/public`. Je garde la convention existante (Q24).

### 3.D bis — Lot d livré (`feature/enrollment-payment`) : contrats exacts à utiliser côté front

Décisions appliquées : paiement par **déclaration manuelle + confirmation admin** (Q18) ; **early-bird puis % du code promo** (cumul) ; **échéances fixées par l'admin à l'acceptation** ; **compte et accès dès la 1re échéance confirmée**. Les réponses de ce lot utilisent `ApiResponse` (aucun appelant front existant), sauf `GET /admin/registrations` et `PATCH …/status` qui gardent leur forme historique (`Page` brute / objet) mais gagnent des champs.

**Statuts d'inscription** : `PENDING` (Nouvelles candidatures) → `PAYMENT_PENDING` (Paiement en attente) → `PAYMENT_TO_CONFIRM` (Paiement à confirmer) → `CONFIRMED`/`COMPLETED` (Inscrits) ; `REJECTED` et `CANCELLED` masqués. Nouveaux champs de `RegistrationResponse` : `payerType, totalAmount, acceptedAt, rejectedReason, learnerId, paymentSummary{installmentCount, confirmedCount, paidAmount, nextDueDate, linkSentAt, lastReminderAt, declaredMethod, declaredReference, declaredHasProof}` (`paymentSummary` null tant qu'aucune échéance n'existe) — de quoi remplir les cartes : « lien envoyé il y a 2 j », « relance », « Wave · réf. », « 2ᵉ échéance le … ».

**Admin candidatures** (SUPER_ADMIN/ADMIN)

| Méthode | Chemin | Corps / effet |
|---|---|---|
| POST | `/admin/registrations/{id}/accept` | corps **optionnel** `{payerType?, totalAmount?, installments?[{amount?, dueDate*}], invoiceRef?, purchaseOrderRef?}` ; `PENDING` uniquement (409). Total absent → calculé ; **400 si la formation n'a pas de prix numérique et que `totalAmount` manque** ; échéances : montants tous absents = répartition égale (reste sur la 1re), tous présents = somme = total, dates croissantes, 12 max ; absentes = un paiement dû sous 2 jours. 409 si l'e-mail est un compte back-office. Envoie le lien de la 1re échéance. |
| POST | `/admin/registrations/{id}/reject` | `{reason*}` ; `PENDING` → `REJECTED` |
| POST | `/admin/registrations/{id}/payments` | paiement reçu hors site : `{installmentNumber?, method*, reference?, invoiceRef?, purchaseOrderRef?, notes?}` → 201, l'échéance passe `DECLARED` (à confirmer) ; `method` = `WAVE|ORANGE_MONEY|VIREMENT|ENTREPRISE|ESPECES` |
| PATCH | `/admin/registrations/{id}/status` | inchangé (Q23). `CANCELLED`/`REJECTED` annulent les échéances ouvertes. **Ne crée pas de compte apprenant.** |

**Admin paiements/accès**

| Méthode | Chemin | Effet |
|---|---|---|
| GET | `/admin/payments?status=&registrationId=&page&size` | `AdminPaymentResponse` (+ `paymentLink`, `proofUrl`, identité du candidat) ; la file « Paiement à confirmer » = `status=DECLARED` |
| POST | `/admin/payments/{id}/confirm` | `DECLARED`/`PENDING` → `CONFIRMED` ; 1re échéance : inscription `CONFIRMED`, place, compte apprenant + invitation, accès, e-mail. 409 si déjà confirmé/annulé |
| POST | `/admin/payments/{id}/reject` | `{reason*}` ; `DECLARED` → `PENDING`, e-mail au candidat ; l'inscription repasse `PAYMENT_PENDING` |
| POST | `/admin/payments/{id}/remind` | relance manuelle (`PENDING`) ; le scheduler fait J+2 puis tous les 2 jours, 3 max |
| GET | `/admin/enrollments` | accès apprenants (`learnerEmail`, `sessionName`, `accessStartsAt/EndsAt`…) |

**Lien de paiement public** (jeton = unique secret ; `404` inconnu, **`410` expiré**) — route front à créer : `{app.frontend.url}{app.frontend.payment-path}/{token}` (défaut `/paiement/{token}`)

| Méthode | Chemin | Effet |
|---|---|---|
| GET | `/payments/{token}` | `{firstName, bootcampTitle, sessionName, sessionStartDate, amount, currency, installmentNumber/Count, dueDate, status, method, reference, hasProof, rejectionReason, totalAmount, schedule[], payTo{phone, methods[]}}` — aucun e-mail/téléphone, aucun jeton |
| POST | `/payments/{token}/declaration` | `{method: WAVE|ORANGE_MONEY|VIREMENT, reference*}` → échéance `DECLARED`, inscription `PAYMENT_TO_CONFIRM`, équipe prévenue (Slack + e-mail). 409 si déjà déclaré/confirmé |
| POST | `/payments/{token}/proof` | multipart champ `file` (image ≤ 5 Mo) ; remplace le justificatif précédent |

**Montants numériques à saisir en back-office** (champs ajoutés aux endpoints existants, tous optionnels) : formation `priceAmount` (+ `currency`, `XOF`) ; session `priceOverrideAmount`, `earlyBirdAmount` (avec `earlyBirdDeadline` existant). Les prix texte (`price`, `priceOverride`, `earlyBirdPrice`) restent l'affichage ; **rien n'est déduit du texte** — tant que `priceAmount` est vide, l'admin doit fournir `totalAmount` à l'acceptation.

**Migration V21** : additive ; statuts historiques et prix texte traversent intacts (test `V21MigrationIT`).

**À faire côté front pour ce lot** : page publique `/paiement/{token}` (instructions Wave/OM, déclaration, upload de capture) ; Candidatures : 4 colonnes branchées sur les nouveaux statuts, « Accepter » → `/accept` (formulaire d'échéances), « Refuser » → `/reject`, « Confirmer »/« Voir la preuve » → `/admin/payments` ; saisie des montants numériques sur les formulaires formation/session ; `AdminInscriptions` : libellés des 3 nouveaux statuts.

**Limites assumées** : pas de réservation de place avant paiement (Q20) → sur-réservation possible, signalée dans les logs ; les justificatifs sont dans le bucket MinIO public sous une clé aléatoire (pas d'URL signée) ; la relance est par e-mail seulement (WhatsApp/WATI reporté, Q21) ; aucun e-mail n'est envoyé au candidat refusé.

### 3.D ter — Lot e livré (`feature/site-content`) : contenus du site pilotés par le back-office

Remplace les constantes de `src/config/siteContent.ts` (« faits métier sans back-office ») et sert de mécanisme générique pour tout texte du site que l'équipe veut modifier sans livraison.

| Méthode | Chemin | Effet |
|---|---|---|
| GET | `/site-settings` (public) | `ApiResponse<{ [clé]: valeur JSON }>` — une clé absente = le site masque le bloc, exactement comme les `null` actuels |
| GET | `/admin/site-settings` | liste `[{key, value, updatedAt, updatedBy}]` (rôles EDITOR, ADMIN, SUPER_ADMIN) |
| PUT | `/admin/site-settings/{key}` | corps `{value: <JSON>}` (20 Ko max) → crée ou remplace ; 400 si clé invalide ou valeur nulle |
| DELETE | `/admin/site-settings/{key}` | retire le contenu (404 si absent) |

**Clés proposées** (mêmes formes que `siteContent.ts`, à saisir depuis le back-office ; rien n'est pré-rempli) :
`coaching.prices` `{deblocage, progression, projet}` (texte libre ou null) · `coaching.coach` `{name}` · `coaching.testimonial` `{quote, author}` · `resources.next-workshop` `{title, date, time}` · `enterprises.client-case` `{name, sector, size, need, result}`.
Toute nouvelle clé est créée par simple `PUT` : aucune migration ni livraison backend pour ajouter un contenu.

**Règle** : tout ce qui est stocké ici est public. Pas de secret, pas de donnée interne.

**À faire côté front** : lire `/site-settings` (un seul appel, à mettre en cache) à la place de `siteContent.ts` ; page admin « Paramètres du site » (formulaires par clé). `references`/`gallery` : les services front `referenceService`/`galleryService` n'ont aucun consommateur, **non implémentés côté back** (à retirer du front ou à rebrancher explicitement).

### 3.D quater — Lot f livré (`feature/course-content`) : programme des formations et espace apprenant

Contrats **exactement ceux de `src/types/course.type.ts`** (JSON brut, sans enveloppe). Remplacent `demoCourse.ts` pour ces appels.

| Méthode | Chemin | Rôle | Effet |
|---|---|---|---|
| GET | `/admin/formations/{formationId}/content` | SUPER_ADMIN, ADMIN, EDITOR ; PARTNER pour **ses** formations | `CourseContent` ; formation sans programme : `modules: []`, réglages par défaut (`updatedAt` null) |
| PUT | `/admin/formations/{formationId}/content` | idem | corps `CourseContent` (arbre complet) → renvoie l'arbre enregistré avec les **identifiants définitifs** (le front doit remplacer ses id temporaires) |
| GET | `/learner/dashboard` | LEARNER | `DashboardData` |
| GET | `/learner/formations/{formationId}/course` | LEARNER inscrit | `LearnerCourse` ; 403 sans accès / pas encore ouvert / expiré (message affichable) |
| PUT | `/learner/lessons/{lessonId}/progress` | LEARNER inscrit | corps `{completed, positionSeconds}` → 204 |

**Règles côté serveur** : `title` du PUT est ignoré (se modifie dans la fiche formation) ; titres obligatoires ≤ 255 ; 50 modules, 100 leçons/module, 30 ressources/leçon ; leçon QUIZ ⇒ `quiz{questionCount≥1, passThreshold 1–100, maxAttempts≥1 ou null=illimité}` ; liens `http(s)://` ; `liveAt` accepté avec ou sans décalage, renvoyé en heure locale `YYYY-MM-DDTHH:mm:ss` ; `accessDuration` ∈ `12_MONTHS|LIFETIME` ; pourcentages 0–100. Éléments retirés du PUT = supprimés (soft delete, la progression reste).

**Vue apprenant** : leçons `DRAFT` absentes ; leçons `SCHEDULED` sans vidéo ; ressources `lockedUntilQuiz` sans `url` (livrée par le lot évaluations après réussite du quiz) ; déblocage séquentiel **imposé** (403 « Terminez d'abord… ») ; une leçon `QUIZ` ne peut pas être marquée terminée par le client (403) ; `LIVE`/`VIDEO`/`RESOURCE` oui.

**Tableau de bord** : `streakDays` = jours consécutifs d'activité (null si aucun) ; `hoursWatched` = durée des vidéos terminées ; `lives` = lives à venir (14 jours, 5 max) ; `resume` = dernière leçon commencée, sinon première leçon publiée à faire ; `courses[].status` = `UPCOMING` avant la date de début sinon `IN_PROGRESS`. **Renseignés par le lot évaluations** : `todos`, `averageQuizScore`, `certificates`, `certificateReady`, statut `CERTIFIED` (aujourd'hui `[]` / null / 0).

**Écarts à connaître** : `trainerName` toujours null (aucun lien formateur ↔ session n'existe encore) ; `timeLabel` = heure de début seule (pas d'heure de fin stockée) ; `place` = « En ligne » ; défauts du certificat configurables par `app.course.default-*` (80 / 70 / 75 / projet requis / « Standard »). Les comptes TRAINER n'ont pas encore accès à l'éditeur.

**Migration V23** : additive, aucune donnée semée.

### 3.D quinquies — Lot g livré (`feature/quiz-project-live`) : évaluations, projet final, appel des lives, suivi de session

Contrats **exactement ceux de `src/types/evaluation.type.ts`** (JSON brut). Les deux écrans d'édition (banque de questions, consigne du projet) n'avaient pas de contrat côté front : ils sont définis ici.

**Apprenant** (`LEARNER`, inscrit, accès ouvert)

| Méthode | Chemin | Effet |
|---|---|---|
| GET | `/learner/formations/{id}/evaluations` | `EvaluationsOverview` : `quizzes[]` (statut `PASSED|FAILED|LOCKED|TO_DO|AVAILABLE`), `project` (null si la formation n'en a pas), `conditions[]` du certificat calculées par le serveur |
| POST | `/learner/quizzes/{quizId}/attempts` | `QuizAttemptStart` ; `quizId` = **id de la leçon QUIZ** ; reprend la tentative ouverte ; 409 si déjà réussi / plus de tentative / quiz sans questions ; 403 module fermé |
| POST | `/learner/quiz-attempts/{id}/submit` | `{answers:{questionId: choiceId}}` → `QuizAttemptResult` ; `review` seulement après réussite ou à la dernière tentative (sinon null) ; réponses inconnues ignorées ; 409 si déjà rendue |
| POST | `/learner/formations/{id}/project/files` | multipart `file` → `ProjectOverview` ; extensions et taille imposées par la consigne ; 10 fichiers max ; 409 si VALIDATED |
| DELETE | `/learner/formations/{id}/project/files/{fileId}` | → `ProjectOverview` ; plus aucun fichier ⇒ `NOT_STARTED` |

**Back-office — contenu** (SUPER_ADMIN, ADMIN, EDITOR ; PARTNER pour ses formations)

| Méthode | Chemin | Corps |
|---|---|---|
| GET/PUT | `/admin/lessons/{lessonId}/quiz` | `{questions:[{id?, text, explanation?, choices:[{id?, label, correct}]}]}` — 1 bonne réponse exactement, 2–8 choix ; GET renvoie en plus `questionCount/passThreshold/maxAttempts` (réglés dans le programme). Le quiz tire `questionCount` questions au hasard dans la banque. |
| GET/PUT/DELETE | `/admin/formations/{id}/project` | `{brief*, deadlineLabel?, acceptedExtensions*, maxSizeMb* (1–50)}` ; GET sans consigne ⇒ champs null |

**Back-office — suivi** (SUPER_ADMIN, ADMIN, TRAINER ; les formateurs voient toutes les sessions tant qu'aucun lien formateur ↔ session n'existe)

| Méthode | Chemin | Effet |
|---|---|---|
| GET | `/admin/sessions/{id}/tracking` | `SessionTracking` |
| PUT | `/admin/sessions/{id}/lives/{liveId}/attendance` | `{presentLearnerIds[]}` → `SessionLive` ; les inscrits non listés sont absents ; 400 si un id n'est pas inscrit à la session ; 404 si ce n'est pas un live de la formation |
| GET | `/admin/sessions/{id}/learners/{learnerId}/project/files` | fichiers rendus avec lien de téléchargement signé (15 min) |
| POST | `/admin/sessions/{id}/learners/{learnerId}/project/review` | `{status: VALIDATED|CHANGES_REQUESTED, message}` (message obligatoire pour demander des corrections) → `ProjectOverview` |

**Règles côté serveur** : voir CLAUDE.md « Évaluations ». Points à connaître côté front : le statut de suivi `ISSUED` n'est jamais renvoyé ; `timeLimitMinutes` et `dueLabel` sont toujours null ; `todos`, `averageQuizScore`, `certificates` du tableau de bord (lot f) restent à brancher sur ces données ; la **correction d'un quiz n'est pas affichable après un échec avec tentatives restantes** (le front doit gérer `review: null`).

**Correctif transverse** : `StorageException` (type/taille de fichier refusés) renvoyait un 500 ; c'est maintenant un 400 avec le message (alumni/projets/partenaires y gagnent aussi).

### 3.D sexies — Lot B livré (`feature/certificates`) : certificats

Maquette : `docs/design/certificat-apercu.png` (logo Model Technologie, nom du lauréat, signataire Patrick Lionnel DOOKO, QR de vérification).

| Méthode | Chemin | Effet |
|---|---|---|
| GET | `/certificates/{publicId}` (**public**) | `ApiResponse<{publicId, status VALID|REVOKED, recipientName, formationTitle, durationLabel, skills[], includesProject, issuedAt, issuer, signatoryName, signatoryTitle, revokedAt}>` ; 404 si inconnu. Aucune donnée de contact. Page front à créer : `/certificats/:publicId` (le QR et l'e-mail y renvoient) |
| GET | `/certificates/{publicId}/pdf` (**public**) | PDF ; 410 si révoqué |
| GET | `/learner/certificates` | `[{publicId, formationId, formationTitle, issuedAt, status, verifyUrl, pdfPath, linkedInUrl}]` (JSON brut) |
| GET | `/admin/certificates?status=` | liste paginée (`ApiResponse`) |
| POST | `/admin/sessions/{sid}/learners/{lid}/certificate` | `{force?, reason?}` → 201 ; 409 conditions manquantes ou déjà délivré ; 403 `force` sans rôle ADMIN ; 400 `force` sans motif |
| POST | `/admin/certificates/{publicId}/revoke` | `{reason*}` ; 409 si déjà révoqué |
| POST | `/admin/certificates/{publicId}/resend` | renvoie l'e-mail avec le PDF |

**Numéro** : `MT-2026-VBA-00042-K7QX` — la maquette montrait `MT-2026-VBA-00042` ; un suffixe aléatoire de 4 caractères est ajouté pour que la page publique ne permette pas de parcourir les certificats numéro par numéro. Retirable si vous préférez le format court (au prix de l'énumération).
**Tableau de bord apprenant** : `stats.certificates`, `certificateReady`, `courses[].status = CERTIFIED` sont désormais renseignés. **Suivi de session** : `certificate` vaut `ISSUED` une fois délivré.
**Front à faire** : page publique de vérification, espace « Mes certificats » (`/espace/certificats`), bouton « Délivrer » et « Révoquer » côté admin, champ `certificateCode` sur la fiche formation (facultatif).
**À fournir plus tard** : le nom du formateur n'apparaîtra sur le certificat qu'avec le lot « formateurs » (lien session ↔ formateur).

### 3.D septies — Lot C livré (`feature/trainers-invoices`) : formateurs, inscriptions manuelles, factures

Aperçu de la facture : `docs/design/facture-apercu.png`.

| Méthode | Chemin | Effet |
|---|---|---|
| PUT | `/admin/bootcamps/sessions/{sessionId}/trainer` | `{trainerId}` (null = retirer) → session (`trainerId`, `trainerName`) ; 400 si le compte n'est pas un formateur actif ; **ADMIN seulement** |
| GET | `/admin/trainers` | formateurs actifs `[{id, fullName, email}]` (`ApiResponse`) |
| POST | `/admin/registrations` | inscription manuelle : `{firstName*, lastName*, email*, country*, profile*, sessionId/bootcampId, company, position, school, phone, promoCode, message}` → 201, `status = PENDING`, `source = ADMIN` (même validations de profil que le site ; aucun e-mail) |
| POST | `/admin/registrations/{id}/cancel` | `{reason*}` ; 409 si déjà annulée/refusée/terminée |
| POST | `/admin/payments/{id}/refund` | `{reason*}` ; échéance `CONFIRMED` uniquement → `REFUNDED` |
| POST | `/admin/registrations/{id}/invoice` | corps facultatif `{buyerName, buyerContact, buyerEmail, buyerAddress, purchaseOrderRef, dueDate, notes}` → 201 ; 409 si non acceptée ou facture déjà en vigueur |
| GET | `/admin/registrations/{id}/invoices` · `/admin/invoices?status=` | historique · liste paginée |
| GET | `/admin/invoices/{number}/pdf` | PDF (filigrane « ANNULÉE » si annulée) |
| POST | `/admin/invoices/{number}/cancel` · `/send` | `{reason*}` · `{to?}` (envoie le PDF par e-mail) |
| GET | `/payments/{token}/invoice` (**public**) | facture de l'inscription ; 404 sans facture en vigueur. `GET /payments/{token}` renvoie `invoiceAvailable` |

**Champs ajoutés** : `RegistrationResponse.source`, `.cancelledReason` ; `AdminPaymentResponse.refundedAt`, `.refundReason` ; `BootcampSessionResponse.trainerId`, `.trainerName` ; statut de paiement `REFUNDED`.
**Règle formateur** : un compte TRAINER ne voit que le suivi, l'appel, les rendus de projet et les certificats **des sessions qui lui sont confiées** (403 sinon) ; le suivi de session affiche son nom, ainsi que le cours apprenant et le certificat.
**À fournir pour des factures conformes** : NINEA, RCCM, adresse, coordonnées bancaires / Wave, mentions de pied de page, et le taux de TVA applicable (variables `APP_INVOICE_SELLER_*`, `APP_INVOICE_VAT_PERCENT`) — rien n'est imprimé tant que ce n'est pas renseigné.
**Front à faire** : « Inscription manuelle » (formulaire), « Annuler » et « Rembourser », sélecteur de formateur sur les sessions, bouton « Facture » (candidatures d'entreprise), lien de téléchargement sur la page de paiement.

### 3.E Formulaires du site (lot b)

| # | Méthode | Chemin | Attendu par le front | Existant au back | Écart |
|---|---|---|---|---|---|
| E1 | POST | `/contact-messages` | `Contact.tsx` : `firstName,lastName,email,phone,company,subject,message` (`subject` ∈ 5 libellés) | Existe (`ApiResponse`, notifie e-mail + Slack) | **OK** · mais **`lastName` est `@NotBlank`** et le front envoie `lastName: ""` quand l'utilisateur saisit un seul mot dans « Nom et prénom » → **400** (Q11) |
| E2 | POST | `/contact-messages` + « type de demandeur » | bascule *particulier / entreprise* dans `Contact.tsx` ; seul `company` est envoyé | pas de champ | **CHAMP MANQUANT** `requesterType` (`PARTICULIER \| ENTREPRISE`) — côté back **et** front |
| E3 | POST | `/diagnostic-requests` (diagnostic entreprise) | Aujourd'hui : `/contact-messages`, `subject="Diagnostic entreprise"`, `role/peopleCount/need/context` **concaténés dans `message`** | Absent | **À CRÉER** (ou rester sur contact — Q10) |
| E4 | POST | `/partner-applications` (candidature partenaire) | Aujourd'hui : `/contact-messages`, `subject="Candidature partenaire formateur"`, `org/domain/linkedin/proposal/references` dans `message` | Absent | **À CRÉER** (idem) |
| E5 | POST | `/newsletter/subscriptions` | `Ressources.tsx` : `setSubscribed(true)` — **aucun appel** | Absent | **À CRÉER** + front à brancher (Q13) |
| E6 | — | Réservation coaching | `/contact?sujet=coaching` (paramètre ignoré par `Contact.tsx`) | Absent | **Hors périmètre** (Q14) |
| E7 | GET/PUT | `/admin/contact-messages`, `…/{id}`, `…/{id}` (PUT), `…/{id}/status?status=` | `AdminMessages` | Existent (`@PreAuthorize` commenté) | OK · à protéger par rôle (C3) |

Valeurs de champs fixées par le front et à reprendre telles quelles dans les DTO : `peopleCount` ∈ `"1 à 5" | "6 à 15" | "16 à 50" | "Plus de 50"` ; `need` ∈ `"Mise à niveau Excel" | "Power BI sur mesure" | "Parcours data sur mesure" | "Je ne sais pas encore"` ; `domain` (partenaire) ∈ `"Gestion de projet & Agile" | "IA appliquée" | "Finance & contrôle de gestion" | "Cybersécurité" | "Communication & leadership" | "Autre domaine"`. → je proposerais des **enums stables** côté back (`NEED_EXCEL_UPGRADE`…) avec libellés laissés au front ; à valider (sinon on stocke le libellé tel quel).

### 3.E bis — Lot b livré (`feature/site-forms`) : contrats exacts à utiliser côté front

Tous les endpoints ci-dessous répondent en `ApiResponse` (`{success, message, data?}`) ; les erreurs en `ErrorResponse` (`message`, `validationErrors`). **429** au-delà de 10 envois par heure et par IP (même compteur pour contact, diagnostic, partenaire, newsletter, par formulaire).

| Endpoint | Corps | Réponse |
|---|---|---|
| `POST /contact-messages` (existant, **rétro-compatible**) | `firstName`, `lastName?` (**désormais facultatif**), `email`, `phone?`, `company?`, `subject?`, `requesterType?` (`PARTICULIER` \| `ENTREPRISE`), `message` | 201, `data` = message (`type: "CONTACT"`) |
| `POST /diagnostic-requests` | `firstName`, `lastName?`, `email`, `phone?`, `company`, `role`, `peopleCount`, `need`, `context?` | 201, `data` = message (`type: "DIAGNOSTIC"`, `requesterType: "ENTREPRISE"`, `details`) |
| `POST /partner-applications` | `firstName`, `lastName?`, `email`, `phone?`, `organization?`, `domain`, `linkedinUrl?`, `proposal`, `references?` | 201, `data` = message (`type: "PARTNER_APPLICATION"`, `details`) |
| `POST /newsletter/subscriptions` | `email`, `source?` | 202 — **même réponse que l'adresse soit connue ou non** ; envoie un e-mail de confirmation (valable 7 jours) |
| `POST /newsletter/subscriptions/confirm` | `token` | 200 (idempotent) ou 400 « Lien de confirmation invalide ou expiré. » |
| `POST /newsletter/subscriptions/unsubscribe` | `token` | 200 (idempotent) ou 400 « Lien de désinscription invalide. » |
| `GET /admin/contact-messages?type=` | — | filtre `CONTACT` \| `DIAGNOSTIC` \| `PARTNER_APPLICATION` ; chaque message expose `type`, `requesterType?`, `details?` |
| `GET /admin/newsletter-subscriptions?status=` | — | `PENDING` \| `CONFIRMED` \| `UNSUBSCRIBED` ; **jamais de jeton** |

**Valeurs d'énumération** (le front doit envoyer le **code**, pas le libellé) :

| Champ | Code → libellé affiché |
|---|---|
| `peopleCount` | `RANGE_1_5` → « 1 à 5 » · `RANGE_6_15` → « 6 à 15 » · `RANGE_16_50` → « 16 à 50 » · `OVER_50` → « Plus de 50 » |
| `need` | `EXCEL_UPGRADE` → « Mise à niveau Excel » · `POWER_BI_CUSTOM` → « Power BI sur mesure » · `DATA_PATH_CUSTOM` → « Parcours data sur mesure » · `UNDECIDED` → « Je ne sais pas encore » |
| `domain` | `PROJECT_AGILE` → « Gestion de projet & Agile » · `APPLIED_AI` → « IA appliquée » · `FINANCE_CONTROL` → « Finance & contrôle de gestion » · `CYBERSECURITY` → « Cybersécurité » · `COMMUNICATION_LEADERSHIP` → « Communication & leadership » · `OTHER` → « Autre domaine » |

Les demandes de diagnostic et candidatures partenaire restent **lisibles dans la page admin « Messages »** : le champ `message` contient le même récapitulatif texte qu'avant (« Fonction : … », « Domaine : … »), et `details` porte les mêmes informations structurées.

**À faire côté front pour ce lot** (aucune modification faite ici) :
1. `Entreprises.tsx` / `Partenaires.tsx` : appeler `/diagnostic-requests` et `/partner-applications` avec les codes ci-dessus (au lieu de `/contact-messages` + texte concaténé). Tant que ce n'est pas fait, les formulaires continuent de fonctionner via `/contact-messages` (désormais sans le 400 sur un nom d'un seul mot).
2. `Contact.tsx` : envoyer `requesterType` (bascule particulier / entreprise) ; lire `?sujet=` (liens du Coaching).
3. `Ressources.tsx` : appeler `POST /newsletter/subscriptions` (aujourd'hui `setSubscribed(true)` sans appel).
4. **Deux nouvelles pages** : `/newsletter/confirmation?token=…` (appelle `/confirm`) et `/newsletter/desinscription?token=…` (appelle `/unsubscribe`) — les liens des e-mails pointent dessus (`app.frontend.url`).
5. Gérer le **429** (« Trop de tentatives… ») sur les quatre formulaires.

### 3.F Autres endpoints consommés par le front (non-régression, hors lots)

| # | Méthode | Chemin | Front | Back | Écart |
|---|---|---|---|---|---|
| F1 | GET | `/testimonials/published` | `usePublishedTestimonials` (`ApiResponse`) | Existe | OK |
| F2 | CRUD | `/admin/testimonials`, `PATCH …/{id}/toggle-published` | `AdminTemoignages` | Existent | OK |
| F3 | GET | `/services`, `/services/{id}` | `ServicesSection` (accueil) | Contrôleur existe ; **absent de `permitAll`** | **À MODIFIER** (à confirmer au runtime) |
| F4 | CRUD | `/admin/services` | `AdminServices` | Existe | OK |
| F5 | GET | `/alumni`, `/projects`, `/projects/{id}` | `useAlumni.ts` → `networkingService` | Existent | OK |
| F6 | CRUD | `/admin/alumni`, `/admin/projects` (+ photo, cover, screenshots, membres) | `useNetworking` | Existent | OK |
| F7 | GET | `/references/published`, `/gallery/published` ; admin `/references`, `/gallery` | `ReferencesSection`, `GallerySection` (accueil), `AdminReferences`, `AdminGalerie` | **Aucun contrôleur** | **À CRÉER** ou à retirer côté front (Q-annexe) |
| F8 | POST | `/masterclass/register` ; GET `/admin/masterclass/{id}/registrations`, `/count` | `MasterclassPage`, `AdminMasterclassPage` | Existent | OK |

---

## 4. Entités, colonnes et enums à créer ou modifier

Numérotation : à partir de la **prochaine version libre**. Si `feature/redisign` est fusionnée d'abord (recommandé), les nouvelles migrations commencent à **V18** ; sinon à V17 et il faudra renuméroter la branche redisign. Toutes les migrations sont **additives** (`ADD COLUMN IF NOT EXISTS`, colonnes nullables ou avec défaut) ; aucune migration appliquée n'est modifiée.

### Lot a — `feature/domain-partner-model`

| Objet | Détail |
|---|---|
| Table `domains` | `id uuid`, `slug` unique, `name`, `description text`, `badge`, `coming_soon bool default false`, `visible bool default true`, `display_order int default 0` + colonnes d'audit `BaseEntity` |
| Table `partners` | `id`, `slug` unique, `name`, `logo_url` (clé MinIO), `bio text`, `website`, `contact_name/email/phone`, `revenue_share_percent numeric(5,2)` (**admin seulement**), `active bool`, audit |
| `bootcamps` + colonnes | `slug` unique (rétro-rempli depuis `title`), `domain_id` FK, `delivered_by` (`INTERNAL`/`PARTNER`, défaut `INTERNAL`, CHECK `PARTNER ⇒ partner_id NOT NULL`), `partner_id` FK, `level`, `format`, `certification_prep text`, `target_roles text[]` |
| Table `bootcamp_related` | `(bootcamp_id, related_bootcamp_id)` pour `relatedFormationIds` (Q6) |
| Enums Java | `DeliveredBy {INTERNAL, PARTNER}` · `FormationLevel {DEBUTANT, INTERMEDIAIRE, AVANCE}` · `FormationFormat {PRESENTIEL, EN_LIGNE, HYBRIDE}` (Q4) |
| DTO | `CreateBootcampRequest`/`UpdateBootcampRequest` : `slug?`, `domainId`, `deliveredBy`, `partnerId?`, `level`, `format`, `certificationPrep?`, `targetRoles?`, `relatedFormationIds?` (tous optionnels pour ne pas casser `BootcampForm`) |
| Compatibilité | `/bootcamps` et `/admin/bootcamps` gardent leurs champs actuels ; les nouveaux champs s'y ajoutent (`@JsonInclude(NON_NULL)` déjà en place). |

### Lot b — `feature/site-forms`

| Objet | Détail |
|---|---|
| `contact_messages` + colonnes | `type` (`CONTACT`/`DIAGNOSTIC`/`PARTNER_APPLICATION`, défaut `CONTACT`), `requester_type` (`PARTICULIER`/`ENTREPRISE`, null), `details jsonb` (champs structurés diagnostic/partenaire) ; assouplir `last_name` (Q11) |
| Table `newsletter_subscriptions` | `id`, `email` unique (lower), `status` (`PENDING`/`CONFIRMED`/`UNSUBSCRIBED`), `source`, `confirmed_at`, `unsubscribed_at`, audit |
| Enums | `ContactType`, `RequesterType`, `NewsletterStatus` |
| Protection | limitation de débit (bucket4j déjà dans le `pom.xml`) ; reCAPTCHA seulement si le front envoie un jeton (Q12) |

### Lot c — `feature/learner-accounts`

| Objet | Détail |
|---|---|
| Table `learners` | `id`, `email` unique (insensible à la casse), `password_hash` (**nullable** jusqu'à l'activation), `first_name`, `last_name`, `phone`, `country`, `active`, `email_verified_at`, `last_login_at`, audit |
| Table `learner_roles` | même schéma que `admin_user_roles` |
| `roles` (données) | `ROLE_LEARNER`, `ROLE_TRAINER`, `ROLE_PARTNER` (migration du même style que V13) |
| `admin_users` + colonne | `partner_id` FK nullable (compte PARTNER limité à ses formations) — selon Q15 |
| Migration de rattrapage | affecter `ROLE_ADMIN` (ou mapper l'ancienne colonne `admin_users.role`) aux admins sans ligne dans `admin_user_roles` — **vérifier d'abord sur une copie de prod** (C4) |
| JWT | claims `roles` (noms stockés, ex. `ROLE_LEARNER`) et `uty` (`ADMIN`/`LEARNER`) ; le front normalise déjà le préfixe `ROLE_` |
| Auth | `UserDetailsService` composite (admin puis learner) ; `JwtAuthenticationFilter` inchangé en logique ; `/auth/me` et reset de mot de passe valables pour les deux |
| Sécurité | matrice §6 ; handlers 401/403 dans `GlobalExceptionHandler` |

### Lot d — `feature/enrollment-payment`

| Objet | Détail |
|---|---|
| Prix numériques | `bootcamps.price_amount bigint`, `bootcamp_sessions.price_override_amount`, `early_bird_amount`, `currency` (défaut `XOF`) ; les chaînes d'affichage existantes **restent** (compat. front) — C6 |
| `registrations` | CHECK de statut recréé (nouvelle migration, V7 intacte) ; + `accepted_at/by`, `rejected_reason`, `learner_id` FK null, `payer_type` (`INDIVIDUAL`/`COMPANY`) |
| `RegistrationStatus` | `PENDING`, **`PAYMENT_PENDING`**, **`PAYMENT_TO_CONFIRM`**, `CONFIRMED`, `COMPLETED`, `CANCELLED`, **`REJECTED`** |
| Table `payments` | `id`, `registration_id` FK, `enrollment_id` FK null, `amount bigint`, `currency`, `method` (`WAVE`/`ORANGE_MONEY`/`VIREMENT`/`ENTREPRISE`), `reference`, `status` (`PENDING`/`DECLARED`/`CONFIRMED`/`REJECTED`/`CANCELLED`), `installment_number`, `installment_count`, `due_date`, `paid_at`, `confirmed_at/by`, `proof_object_key` (MinIO), `public_token` unique + `token_expires_at`, `reminder_count`, `last_reminder_at`, `invoice_ref`, `purchase_order_ref`, `notes`, audit |
| Table `enrollments` | `id`, `learner_id` FK, `session_id` FK, `registration_id` FK unique, `status` (`ACTIVE`/`SUSPENDED`/`COMPLETED`/`CANCELLED`), `access_starts_at`, `access_ends_at`, audit |
| Enums | `PaymentMethod`, `PaymentStatus`, `EnrollmentStatus`, `PayerType` |

**Lien avec `Registration` (pas de duplication)** : `Registration` reste la **candidature** (données du formulaire public + colonne du Kanban). `Learner` est créé **à partir** de ces données à la confirmation du paiement. `Enrollment` = accès (learner ↔ session) créé à ce moment, `registration_id` unique. `Payment` est rattaché à la `Registration` (1..N pour les échéances). Aucune donnée d'identité n'est copiée dans `Payment`/`Enrollment`.

**Machine d'états** (colonnes du front ↔ statuts) :

| Colonne front | Statut `Registration` | Transition sortante |
|---|---|---|
| Nouvelles candidatures | `PENDING` | `accept` → `PAYMENT_PENDING` · `reject` → `REJECTED` |
| Paiement en attente | `PAYMENT_PENDING` | candidat déclare → `PAYMENT_TO_CONFIRM` · annulation → `CANCELLED` |
| Paiement à confirmer | `PAYMENT_TO_CONFIRM` | `confirm` → `CONFIRMED` · preuve rejetée → `PAYMENT_PENDING` |
| Inscrits | `CONFIRMED`, `COMPLETED` | `CONFIRMED → COMPLETED` (existant) |
| (masquées) | `CANCELLED`, `REJECTED` | — |

**Compatibilité** : l'ancien flux manuel `PENDING → CONFIRMED` via `PATCH /status` reste autorisé pour les ADMIN (la page `AdminInscriptions` et les habitudes actuelles ne cassent pas) — Q23. Un statut inconnu côté front ne casse pas l'API, mais `AdminInscriptions`/`AdminCandidatures` devront afficher les nouveaux libellés (§9).

---

## 5. Écarts de noms et de formes (JSON ↔ TypeScript)

| # | Où | Front | Back | Action |
|---|---|---|---|---|
| N1 | JWT | lit `roles[]` | absent | **Back** : ajouter le claim |
| N2 | `AuthResponse.user` | `{id,email,fullName,role}` | `{id,email,fullName,primaryRole,roles}` | **Front** : s'aligner sur `roles` (le back garde `primaryRole`) |
| N3 | `Registration` | `country, profile, school` | absents de `RegistrationResponse` | **Back** : ajouter |
| N4 | Formats | `Formation.format` = `EN_LIGNE`/`HYBRIDE` ; `BootcampSession.format` = `REMOTE`/`HYBRID` | enum session = `REMOTE`/`HYBRID` | **Incohérence interne au front** — à signaler (Q4) ; le back servira ce que déclare `formation.type.ts` |
| N5 | `BootcampSession.schedule` | `string` (non nul) | absent sur `develop` ; nullable sur redisign | **Back** : ajouter ; **front** : accepter `null` ou fournir un défaut côté API |
| N6 | `Formation.tagline/colorKey` | non déclarés | exposés (redisign) | **Front** : déclarer et consommer |
| N7 | Contact `lastName` | envoie `""` | `@NotBlank` | Q11 |
| N8 | Deux types `Bootcamp` | `types/index.ts` (`nextSession: string`) ≠ `types/bootcamp.type.ts` (`BootcampSession`) | `BootcampSession` | **Front** : supprimer le type obsolète |
| N9 | Statut inscription | 4 valeurs | 7 valeurs (lot d) | **Front** : élargir le type |
| N10 | Prix | `price: string` | + montants numériques (lot d) | additif, aucune rupture |
| N11 | Dates | `"YYYY-MM-DD"`, ISO sans fuseau pour `createdAt` | `LocalDate` / `LocalDateTime` | OK |

---

## 6. Matrice de sécurité cible (`SecurityConfig`)

| Zone | Règle |
|---|---|
| `GET` `/formations`, `/formations/**`, `/domains`, `/partners`, `/bootcamps`, `/bootcamps/**`, `/services`, `/services/**`, `/testimonials/published`, `/alumni`, `/projects/**`, `/sessions/**`, `/promo-codes/validate`, `/payments/{token}` | `permitAll` (le jeton protège `/payments/{token}`) |
| `POST` `/auth/login`, `/auth/forgot-password`, `/auth/reset-password`, `/registrations`, `/contact-messages`, `/diagnostic-requests`, `/partner-applications`, `/newsletter/subscriptions`, `/masterclass/register`, `/payments/{token}/declaration`, `/payments/{token}/proof` | `permitAll` |
| `/auth/me`, `/auth/logout`, `/auth/change-password` | authentifié (tout rôle) |
| `/admin/**` (contenu, formations, domaines, partenaires, messages, témoignages, alumni, projets) | `hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')` |
| `/admin/registrations/**`, `/admin/payments/**`, `/admin/enrollments/**`, `/admin/learners/**`, `/admin/promo-codes/**` | `hasAnyRole('SUPER_ADMIN','ADMIN')` (Q22) |
| Éditeur de formation TRAINER/PARTNER (lot 5) | hors périmètre actuel |
| `/learner/**` (lot 5) | `hasRole('LEARNER')` |

Les noms de rôles en base portent le préfixe `ROLE_` : `hasRole('ADMIN')` convient. **Ne pas activer l'enforcement avant la migration de rattrapage (C4).**

---

## 6 bis. Garanties de non-régression (prod existante)

1. **Migrations additives uniquement**, sans réécriture des V1–V16.
2. `/bootcamps`, `/admin/bootcamps`, `/registrations`, `/contact-messages`, `/auth/*` : champs et enveloppes **inchangés** ; les ajouts sont facultatifs en entrée et omis quand nuls en sortie.
3. Rétro-remplissage déterministe (slug depuis le titre, suffixe `-2` en cas de doublon) et **aucune** colonne `NOT NULL` ajoutée sans valeur par défaut.
4. Jetons JWT déjà émis : toujours valides (le filtre recharge les droits depuis la base, il ne lit pas `roles` dans le jeton).
5. L'enforcement des rôles n'est activé qu'après vérification que tous les admins existants ont un rôle.
6. Ancien flux `PENDING → CONFIRMED` conservé.
7. Les formulaires actuels (`/contact-messages`) continuent de fonctionner tant que le front n'a pas basculé sur les endpoints dédiés.

---

## 7. Tests

État : **aucun test**. Plan : `spring-boot-starter-test` (déjà présent) + `spring-security-test` + **Testcontainers PostgreSQL** (Flyway joue les vraies migrations, `jsonb`/`text[]` fonctionnent) ; `@SpringBootTest` + `MockMvc`. Un test d'intégration minimum par controller nouveau ou modifié (cas nominal + 401/403 + validation 400). `./mvnw test` suppose **Docker démarré** (Q26).

---

## 8. Décisions validées (2026-10-02) et questions restantes

**Validées par Lionnel** : Q1 (fusion de `feature/redisign` avant le lot a → migrations à partir de **V18**) · Q7 (domaine « Data & BI » et rattachement des bootcamps existants par migration) · Q10 (endpoints dédiés persistés dans `contact_messages`) · Q15 (TRAINER et PARTNER = comptes `AdminUser` avec rôles distincts) · Q17 (compte apprenant créé à la confirmation du paiement, lien « définir mon mot de passe ») · Q18 (paiement par **déclaration manuelle** + confirmation admin ; API mobile money plus tard).

**Appliquées par défaut (recommandations du tableau ci-dessous, non contestées)** : Q2 (règle d'enveloppe §2), Q3, Q4, Q5, Q6, Q8, Q9. Les questions Q11–Q14, Q16, Q19–Q29 seront reprises au lot concerné.

Précisions issues de l'implémentation du lot a :
- `level` et `format` sont **nullables** après rétro-remplissage (aucune valeur métier inventée) : tant qu'un admin ne les renseigne pas, ils sont absents du JSON.
- Une formation **sans domaine** n'apparaît pas dans `/formations` (le front exige `domain`) ; elle reste visible dans `/bootcamps`. Les formations existantes sont toutes rattachées à « Data & BI » par la migration.
- `Domain`/`Partner` ajoutés dans le module `training`.

### Questions ouvertes (avec ma recommandation par défaut)

**À trancher avant le lot a**

| Q | Question | Recommandation |
|---|---|---|
| Q1 | Fusionner `feature/redisign` (V17, contenu riche, FK témoignage, correctif sécurité `/bootcamps/{id}`) dans `develop` **avant** le lot a ? | **Oui** : le contrat `Formation` en dépend ; nouvelles migrations à partir de V18. |
| Q2 | Règle d'enveloppe (§2) ? | Celle proposée en §2. |
| Q3 | `Formation` = `Bootcamp` enrichi (même table, même `id`), `/bootcamps` conservé ? Que devient `category` (`bi`, `python`…) face à `domain` ? | Oui ; `category` conservé (non exposé comme filtre), à déprécier plus tard. |
| Q4 | Formats : le front a **deux jeux** (`EN_LIGNE`/`HYBRIDE` pour la formation, `REMOTE`/`HYBRID` pour la session). | Le back sert les valeurs de `formation.type.ts` pour la formation ; le front devrait unifier. |
| Q5 | `targetRoles` (filtre « Métier ») : champ confirmé ? tags libres ou référentiel ? | Tags libres `text[]` en v1. |
| Q6 | `relatedFormationIds` : curation manuelle ou calcul ? | Table de liaison éditable, repli « même domaine ». |
| Q7 | Rétro-remplissage : créer le domaine « Data & BI » et y rattacher les bootcamps existants **par migration** ? niveau/format par défaut ? Sinon `/formations` renvoie `[]` au déploiement (catalogue vide). | Migration pour domaine + slug ; niveau/format laissés nullables puis renseignés en admin. |
| Q8 | Quelles sessions dans `/formations/sessions` ? | `published` et statut `OPEN` ou `UPCOMING`. |
| Q9 | Partenaires publics : seulement ceux ayant ≥ 1 formation publiée ? logo en MinIO ? | Oui et oui (même schéma que `alumni/{id}/photo`). |

**Lot b**

| Q | Question | Recommandation |
|---|---|---|
| Q10 | Endpoints dédiés (`diagnostic-requests`, `partner-applications`) ou rester sur `contact-messages` ? | Endpoints dédiés (validation typée) **persistés dans la même table** (`type` + `details jsonb`) : la page admin Messages continue de tout lister. |
| Q11 | `lastName` obligatoire alors que le front envoie `""` pour un nom d'un seul mot. | Rendre `lastName` facultatif pour les formulaires de contact. |
| Q12 | Protection anti-spam des formulaires publics (aucune aujourd'hui sur contact). | Limitation de débit bucket4j maintenant ; reCAPTCHA seulement si le front ajoute le jeton. |
| Q13 | Newsletter : table interne ou liste Brevo ? double opt-in ? | Table interne + double opt-in en v1 ; export/synchro Brevo plus tard. |
| Q14 | Coaching : réservation hors périmètre ? | Oui (le front passe par le contact). |

**Lot c**

| Q | Question | Recommandation |
|---|---|---|
| Q15 | TRAINER et PARTNER : comptes `AdminUser` + rôle (back-office) ou entités `Learner` ? | `AdminUser` + rôle, `partner_id` pour PARTNER ; `Learner` réservé à LEARNER. |
| Q16 | Unicité d'e-mail entre `admin_users` et `learners` ? | Unicité globale vérifiée au service. |
| Q17 | Création du `Learner` : à la confirmation du paiement ? identifiants initiaux : lien « définir mon mot de passe » (jeton de reset) ? chemin du lien (`/admin/reset-password` est en dur) ? | À la confirmation ; lien de définition de mot de passe ; chemin neutre (le front devra exposer une route non-`/admin`). |
| Q25 | `AuthResponse.user` : ajouter `role` en alias ou aligner le front sur `roles` ? | Aligner le front. |

**Lot d**

| Q | Question | Recommandation |
|---|---|---|
| Q18 | Paiement : déclaration manuelle + confirmation admin (cohérent avec « Voir la preuve ») ou API opérateur (Wave/OM) avec webhooks ? « Lien de paiement » = page tokenisée du site (à créer côté front) ou lien Wave brut ? | Manuel + page tokenisée en v1 ; modèle `Payment` prêt pour des webhooks. |
| Q19 | Montants : ajouter `price_amount` (XOF entier) sur formation et session ; règle early-bird ; la remise promo s'applique-t-elle au total ; combien d'échéances et quel calendrier (« payé en 2 fois ») ? | Montants numériques ; promo sur le total ; échéances configurées à l'acceptation par l'admin. À préciser par vous. |
| Q20 | Réservation de place : à `CONFIRMED` (comportement actuel) ou dès `PAYMENT_PENDING` ? | Garder `CONFIRMED` en v1 (zéro régression) ; risque de sur-réservation à noter. |
| Q21 | Notifications : Brevo + WATI (README) ou SMTP + Slack existants ? relance J+2 par scheduler Spring (le projet en utilise déjà un) ? | E-mail via SMTP existant + scheduler en v1 ; WhatsApp reporté tant que les accès WATI ne sont pas fournis. |
| Q22 | EDITOR peut-il voir candidatures et paiements ? | Non : SUPER_ADMIN et ADMIN seulement. |
| Q23 | Conserver `PENDING → CONFIRMED` direct pour les ADMIN ? | Oui. |
| Q24 | Préfixe `/public/...` du README ou convention actuelle `/api/v1/<pluriel>` ? | Convention actuelle. |

**Transverses**

| Q | Question | Recommandation |
|---|---|---|
| Q26 | Tests : Testcontainers (Docker requis) acceptable ? | Oui. |
| Q27 | Mettre à jour le `CLAUDE.md` backend dans la PR du lot a ? | Oui. |
| Q28 | `/references` et `/gallery` (accueil, aucun contrôleur) : créer ou retirer côté front ? | Décision produit ; hors des 4 lots. |
| Q29 | README annonce Spring Boot 3.4 ; le `pom.xml` est en **3.2.5**. Montée de version voulue ? | Non, pas dans ces lots. |

---

## 9. À corriger côté front (aucune modification faite)

0. **Pages « mot de passe oublié » et « réinitialisation » cassées** : `AdminForgotPassword.tsx` et `AdminResetPassword.tsx` appellent `forgotPassword` / `resetPassword` issus de `useAuth()`, qui ne les expose plus (régression du refactor du contexte). Il faut les rebrancher sur `authService.forgotPassword/resetPassword`. À corriger **avant** d'ouvrir les comptes apprenants (le flux de définition de mot de passe en dépend).
1. `useAuth.signOut()` n'appelle pas `POST /auth/logout` : le jeton reste valide jusqu'à expiration.
2. `Contact.tsx` ignore `?sujet=` (liens `Coaching` → `/contact?sujet=coaching`).
3. `Contact`, `Entreprises`, `Partenaires` : `lastName: ""` pour un nom d'un seul mot (→ 400 tant que Q11 n'est pas tranchée) ; `Contact` n'envoie pas le type de demandeur.
4. `Ressources.tsx` : newsletter non branchée.
5. `formation.type.ts` : ajouter `tagline` et `colorKey` ; `FormationDetail` les remplace par `formation.title` / `"accent"` en dur.
6. Formats : `EN_LIGNE`/`HYBRIDE` vs `REMOTE`/`HYBRID` (N4).
7. `RegistrationStatus` : 4 valeurs → 7 ; `AdminCandidatures` : remplacer les deux colonnes « en cours », brancher « Accepter » sur `/accept` (et non plus sur `CONFIRMED`), « Inscription manuelle ».
8. Types/code mort ou contradictoires : `types/index.ts` `Bootcamp.nextSession: string` ; `services/api/bootcampService` (attend une `Page`, le back renvoie un tableau) et ses hooks `useBootcamps*`/`usePublishedBootcamps` (sans consommateur) ; `services/api/alumniService.ts`, `projectService.ts` ; `promoCodeService.validate` (`/admin/promo-codes/validate/{code}` inexistant) ; `Adminbootcampservice.ts` (casse du nom de fichier).
9. `authService.getCurrentUser` jamais utilisé ; type `AuthUser.role`.
10. `formationService` : le repli sur le mock devrait être limité au mode dev (en prod il masque une panne et ne couvre pas le cas « API vide »).
11. Lien « Mot de passe oublié » de `/connexion` → `/admin/forgot-password` (chemin neutre à prévoir pour les apprenants).
12. `RegistrationModal` : la branche `429` n'a aujourd'hui aucun émetteur côté back (pas de limitation de débit sur `/registrations`) ; la branche `403` (reCAPTCHA) non plus (§10).
13. Routes admin `references`/`galerie` encore déclarées, entrées de menu retirées ; l'accueil appelle `/references/published` et `/gallery/published` (aucun contrôleur).
14. Front admin : prévoir les pages Apprenants, Domaines, Partenaires, Paiements quand les endpoints seront livrés.

---

## 10. Constats backend hors périmètre direct, mais qui touchent ces lots

| # | Constat | Traitement proposé |
|---|---|---|
| H1 | `GlobalExceptionHandler` capture `Exception` : `ResponseStatusException` (409 session complète, 400 profil) et `AccessDeniedException` (403) deviennent très probablement des **500** (à confirmer par test). Le contrôle de rôle du lot c en dépend. | Ajouter handlers `ResponseStatusException`, `AccessDeniedException`, `AuthenticationException` (lot c). |
| H2 | `RegistrationServiceImpl.register` **ignore** le booléen de `recaptchaService.verify()` (contrairement à `MasterclassServiceImpl`) : le reCAPTCHA n'est pas appliqué à l'inscription. | Appliquer le résultat (lot d, ou correctif séparé si vous préférez). |
| H3 | `GET /services` et `/services/{id}` : non listés dans `permitAll` (lecture du code ; à confirmer en exécution). | Correctif `SecurityConfig` (déjà proposé en tâche séparée). |
| H4 | Healthcheck Docker du `Dockerfile` figé sur le port 8080 alors que le dev tourne en 8081. | Tâche séparée déjà proposée. |
| H5 | `JWT_SECRET` : sur les environnements dont le secret est un placeholder (< 512 bits), la vérification JWT échoue. Corrigé en local uniquement. | À vérifier pour staging/prod avant tout lot touchant l'auth. |
| H6 | Aucun scheduler métier à ce jour hors `AuthCleanupScheduler` : la relance J+2 sera le premier. | Lot d. |

---

## 11. Ordre de livraison et branches (inchangé par rapport à la consigne)

0. Décisions Q1 → Q9 (surtout Q1, Q7).
1. `feature/domain-partner-model` (lot a) — débloque `/formations`, `/domains`, `/partners` ; front : suppression progressive du mock.
2. `feature/site-forms` (lot b).
3. `feature/learner-accounts` (lot c) — **préalable de sécurité** pour tout le reste (C2 → C5, H1).
4. `feature/enrollment-payment` (lot d) — dépend de c (compte apprenant) et de C6 (montants numériques).

Chaque lot : plan → code → `./mvnw test` → commit → push → PR vers `develop`, avec la liste des endpoints ajoutés ou modifiés dans la description.
