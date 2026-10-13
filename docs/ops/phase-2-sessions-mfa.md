# Phase 2 — Sessions sécurisées et double authentification obligatoire

Suite de l'audit de sécurité (`data-mastery-hub/docs/security/audit-securite-et-architecture.md`, constats E1, E2, M5, M6, M7). Ce lot touche le backend (branche `sec/phase-2-sessions-mfa`) et le front (même nom de branche).

## 1. Ce qui change

### Sessions (E2)
| Avant | Maintenant |
|---|---|
| Jeton de 24 h dans `localStorage` (lisible par tout script de la page), non révoqué à un changement de mot de passe | **Jeton d'accès de 15 minutes gardé en mémoire** de la page, jamais écrit dans le stockage du navigateur |
| Pas de refresh | **Cookie `HttpOnly; Secure; SameSite=Strict`** (jeton de rafraîchissement opaque, 256 bits) limité au chemin `/api/v1/auth` : invisible du JavaScript. Au chargement d'une page, la session est rouverte en silence (`POST /auth/refresh`) ; un jeton expiré est renouvelé sans que l'utilisateur le voie |
| Un jeton volé reste valable 24 h | Le jeton de rafraîchissement **change à chaque usage** ; rejoué (vol probable), il révoque la session. Deux onglets qui rafraîchissent en même temps sont tolérés (10 s) |
| Aucune notion de session | Une ligne `auth_sessions` par appareil (empreinte du jeton, IP, navigateur). Personnel : 8 h d'inactivité, 7 jours au plus. Apprenants : 14 jours d'inactivité, 60 jours au plus |
| Changement de mot de passe, de rôle, désactivation sans effet sur les jetons déjà émis | **Version de sécurité** par compte, copiée dans chaque jeton : ces événements (et la réinitialisation du MFA) invalident d'un coup tous les jetons et toutes les sessions. Après un changement de mot de passe, il faut se reconnecter partout |
| Clé JWT jamais renouvelée | **Rotation** : `JWT_PREVIOUS_SECRET` reste accepté en lecture le temps de la transition (identifiant de clé `kid`) |
| — | `/auth/refresh` et `/auth/logout` exigent un en-tête `Origin` appartenant à `CORS_ALLOWED_ORIGINS` (défense contre les requêtes forgées depuis un autre site). `Cache-Control: no-store` sur les réponses qui portent un jeton |

### Double authentification obligatoire du personnel (E1)
- Concerne **tous** les comptes du back-office : Super Admin, Admin, Éditeur, Formateur, Partenaire. Les apprenants n'y sont pas soumis.
- Application d'authentification (TOTP, RFC 6238) : Google Authenticator, Microsoft Authenticator, Authy, 1Password, Bitwarden…
- **Première connexion** : écran d'enrôlement obligatoire (clé à saisir ou lien `otpauth://`), confirmation par un premier code, puis **10 codes de secours** à usage unique, affichés une seule fois. Le back-office reste inaccessible tant que ce n'est pas fait.
- **Connexions suivantes** : après le mot de passe, le code à 6 chiffres (ou un code de secours).
- Le lien/code par e-mail et la connexion Google **ne suffisent plus** pour le personnel : ils ne sont que le premier facteur, le code est demandé ensuite.
- Un jeton d'accès de personnel sans second facteur validé est refusé par le serveur sur toutes les routes ; le « défi » de 5 minutes délivré après le mot de passe ne donne accès à aucune route autre que `/auth/mfa/*`.
- Un code déjà utilisé ne sert pas deux fois ; 5 codes faux verrouillent la vérification 15 minutes.
- Secrets TOTP **chiffrés en base** (AES-256-GCM) ; codes de secours conservés sous forme d'empreinte à clé (HMAC-SHA256).
- **Réinitialisation** (téléphone perdu) : un *autre* Super Admin, depuis *Comptes* → « Réinitialiser le MFA ». Les sessions du compte sont fermées, le titulaire est prévenu par e-mail et reconfigure un appareil à sa prochaine connexion. Personne ne peut réinitialiser son propre MFA.
- Les Super Admins voient l'état du MFA de chaque compte (« Double authentification » / « À configurer »).

### Comptes (M5, M6, M7)
- **Verrouillage par couple (e-mail, adresse IP)** : 5 échecs bloquent ce couple 15 minutes. Un tiers qui s'acharne sur l'adresse d'un administrateur ne l'empêche plus de se connecter depuis son propre réseau ; le blocage s'applique aussi aux adresses inexistantes (rien ne révèle l'existence d'un compte). Le verrouillage du compte seul n'intervient plus qu'après 20 échecs répartis sur de nombreuses adresses.
- **Mots de passe** : 12 caractères minimum pour le personnel, 10 pour les apprenants (72 au plus : limite de BCrypt) ; refus des mots de passe courants et de leurs variantes évidentes (« Password123! », « P@ssw0rd »), des suites de caractères et de l'adresse e-mail du compte. Pas d'obligation de majuscule ou de symbole : la longueur protège mieux. Le serveur explique le refus ; le lien de réinitialisation reste utilisable après un refus.
- **Code de connexion à 6 chiffres** (par e-mail) conservé sous forme d'empreinte à clé secrète (une fuite de la table ne permet plus de le retrouver).
- **Journaux** : adresses e-mail masquées (`aw***@gmail.com`).

## 2. À faire avant le déploiement

1. **`MFA_ENCRYPTION_KEY`** — obligatoire en staging et en production (les fichiers compose refusent de démarrer sans) :
   ```bash
   openssl rand -base64 32
   ```
   À conserver dans le gestionnaire de mots de passe de l'entreprise. **Ne jamais la changer** une fois des comptes enrôlés (leurs secrets deviendraient illisibles : il faudrait réinitialiser le MFA de tout le monde). Elle est volontairement distincte de `JWT_SECRET` (dont la rotation reste possible).
2. **Même domaine pour le site et l'API** : le cookie est `SameSite=Strict` ; le site (ex. `staging.model-technologie.com`) et l'API (ex. `api-staging.model-technologie.com`) doivent partager le même domaine principal (`model-technologie.com`), sinon le navigateur n'enverra pas le cookie et personne ne pourra rester connecté.
3. **`CORS_ALLOWED_ORIGINS`** doit contenir l'adresse exacte du site (c'est aussi la liste blanche de l'en-tête `Origin` des routes à cookie).
4. **Connexion de tout le monde** : les anciens jetons (24 h, `localStorage`) ne sont plus reconnus ; chacun se reconnecte une fois et, pour le personnel, configure son application d'authentification. Prévenez l'équipe avant de déployer et gardez **au moins deux Super Admins** (pour qu'un oubli de téléphone ne bloque personne).
5. Facultatif : `JWT_EXPIRATION` (15 min par défaut), `AUTH_COOKIE_SECURE` (vrai par défaut ; `false` seulement en local en HTTP), `AUTH_REFRESH_*`, `AUTH_THROTTLE_*`, `AUTH_PASSWORD_MIN_*`, `JWT_PREVIOUS_SECRET` (rotation).

Développement local : le profil `dev` désactive `Secure` sur le cookie. Le MFA reste obligatoire (le compte de développement s'enrôle une fois) ; une clé de chiffrement de développement est dérivée automatiquement hors staging/production. `AUTH_MFA_ENFORCED=false` le désactive, **uniquement en local ou en test** (jamais en staging/production).

## 3. Procédures

**Rotation de la clé JWT** : 1) `JWT_PREVIOUS_SECRET` = ancien secret, `JWT_SECRET` = nouveau (`openssl rand -base64 64`) ; 2) redéployer ; 3) après 24 h (durée d'une session de personnel), retirer `JWT_PREVIOUS_SECRET`.

**Perte de l'accès d'un Super Admin** (appareil et codes de secours perdus, aucun autre Super Admin) — dernier recours, sur la base de données :
```sql
UPDATE admin_users SET mfa_secret_enc = NULL, mfa_enabled_at = NULL, mfa_last_step = NULL,
       mfa_failed_attempts = 0, mfa_locked_until = NULL, security_version = security_version + 1
 WHERE email = 'adresse@exemple.com';
DELETE FROM admin_recovery_codes WHERE admin_user_id = (SELECT id FROM admin_users WHERE email = 'adresse@exemple.com');
UPDATE auth_sessions SET revoked_at = NOW() WHERE subject_id = (SELECT id FROM admin_users WHERE email = 'adresse@exemple.com');
```
Le compte devra enrôler un nouvel appareil à sa prochaine connexion.

**Couper une session suspecte** : désactiver le compte (*Comptes* → Modifier) ou réinitialiser son mot de passe : toutes ses sessions se terminent aussitôt.

## 4. Vérifications effectuées

- **Tests automatisés** : `TotpTest` (vecteurs officiels de la RFC 6238 et RFC 4648), `MfaCryptoTest`, `PasswordPolicyTest`, `JwtTokenProviderTest` (types de jetons, rotation de clé, falsification, algorithme « none »), `LoginThrottleTest`, `SessionsAndMfaIT` (23 scénarios : enrôlement, vérification, rejeu d'un code, code de secours, verrouillage, défi inutilisable sur les routes, cookie `HttpOnly/Secure/SameSite`, rotation et rejeu du jeton de rafraîchissement, deux onglets, origine refusée, session expirée, déconnexion, changement de mot de passe, changement de rôle, réinitialisation du MFA par un Super Admin, apprenants inchangés, mot de passe faible).
- **Parcours réel dans un navigateur** (backend et front lancés en local sur une copie de la base) : connexion → enrôlement → codes de secours → arrivée au back-office → rechargement de la page (session rouverte par le cookie, aucun jeton dans `localStorage`) → déconnexion (le rafraîchissement est ensuite refusé) → reconnexion avec un code de secours (un faux code est refusé) → jeton d'accès de 15 s expiré en cours d'utilisation (rafraîchissement silencieux, la page continue sans reconnexion) → liste des comptes avec l'état du MFA.
- **Non vérifié** : le rendu visuel des écrans de double authentification (captures d'écran impossibles dans l'environnement de test : contrôle fait sur le texte et le comportement) ; l'application d'authentification réelle sur téléphone (le code a été calculé par le même algorithme standard) ; le comportement derrière Traefik/Dokploy (domaines, en-têtes).

## 5. Limites connues

- Pas de QR code : clé à recopier ou lien `otpauth://` (suffisant sur mobile). Un QR code demande d'ajouter une dépendance au front.
- Le frein de connexion par (e-mail, IP) est en mémoire (une instance) ; à déplacer vers un stockage partagé si le backend est répliqué.
- Pas encore de page « mes sessions / appareils connectés » (les données existent dans `auth_sessions`).
- Le MFA est optionnel/absent pour les apprenants.
- Les e-mails d'alerte « nouvelle connexion depuis un appareil inconnu » ne sont pas encore envoyés.
