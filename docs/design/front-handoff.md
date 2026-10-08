# Passation frontend — brief pour l'agent qui travaille sur `data-mastery-hub`

Le backend est terminé (lots A à H, 224 tests verts). Ce document dit **quoi construire côté front, dans quel ordre, et où sont les contrats exacts**.

- Frontend : `D:\modeltech\data-mastery-hub` (React 18, Vite, TypeScript, Tailwind, shadcn/ui, TanStack Query v5, React Router v6, Zod, React Hook Form). Lire son `CLAUDE.md` en premier.
- Backend : `D:\modeltech\modeltech-backend`. **Source de vérité des contrats : `docs/design/api-contract.md`** (sections 3.A bis, 3.D bis à 3.D undecies, 3.E bis, 3.F, 5 « écarts de noms », 9 « À corriger côté front »). Ne rien deviner : si un champ n'y est pas, le lire dans le code du contrôleur / du DTO correspondant du backend, ne pas l'inventer.
- API locale : `http://localhost:8081/api/v1` (voir `.env.local` du front). Lancement du backend : `.\scripts\run-local.ps1` dans le dossier backend (Docker doit tourner). Le front tourne sur `http://localhost:8080`.

## Règles de travail (non négociables)

1. **Une branche par lot**, créée depuis la branche courante du front après avoir vérifié `git status`. Des modifications non commitées préexistent (`README.md`, `index.html`) : **ne pas les inclure** dans vos commits ni les écraser.
2. Chaque lot : implémenter → `npm run build` sans erreur → vérifier dans le navigateur contre le vrai backend (pas de mock) → commit (message `feat(front-<lot>): …`) → push → donner le lien de PR. **Ne pas enchaîner** le lot suivant sans mon accord.
3. Suivre les conventions existantes : un service par entité dans `src/services/api/`, un hook React Query par entité dans `src/hooks/`, types dans `src/types/`, composants `src/components/ui/` (shadcn) **non modifiables**. Formulaires = React Hook Form + Zod. Textes de l'interface en **français**.
4. Deux familles de réponses : endpoints neufs en enveloppe `ApiResponse {success, message, data, pagination?}` ; endpoints `/learner/**`, `/admin/formations/*/content`, `/admin/sessions/*/tracking`, évaluations et `/auth/*` en **JSON brut**. Le contrat dit lequel pour chaque route.
5. Aucune donnée codée en dur qui doit venir du back-office. Pas de repli sur des données de démonstration en production (point 10 de la section 9 du contrat).
6. Ne jamais afficher ni logger un jeton, un code de connexion ou une clé. Pas de `dangerouslySetInnerHTML` sur du contenu venant de l'API.
7. En cas d'écart entre le contrat et le comportement réel du backend : le signaler précisément (route, requête, réponse) au lieu de contourner côté front.

## Ordre de livraison (validé)

### Lot F1 — Connexion (`feature/front-auth`)
Contrat : §3.A bis et **§3.D undecies**.
- **Corriger d'abord** la régression §9.0 : `AdminForgotPassword.tsx` et `AdminResetPassword.tsx` doivent appeler `authService.forgotPassword/resetPassword` (le contexte `useAuth` ne les expose plus). Puis §9.1 (`signOut` doit appeler `POST /auth/logout`) et §9.11 (chemin « mot de passe oublié » neutre pour les apprenants).
- Page `/connexion` : au chargement, `GET /auth/options` ; n'afficher que les modes actifs.
  - Mot de passe (existant) ; gérer **429** (« Trop de tentatives… ») avec le message du serveur et proposer le lien par e-mail.
  - « Recevoir un lien de connexion » : saisie e-mail → `POST /auth/passwordless/request` → écran « Entrez le code reçu ou cliquez sur le lien » (6 chiffres, `POST /auth/passwordless/verify {email, code}`), renvoi possible après 60 s, messages d'erreur 400 génériques.
  - Route `/connexion/lien?token=…` : appelle `verify {token}` puis redirige (apprenant → `/espace`, back-office → `/admin`).
  - Bouton Google : Google Identity Services avec `googleClientId` renvoyé par `/auth/options` (ne pas le coder en dur) ; envoyer le `credential` à `POST /auth/google` ; 401 → « Aucun compte ne correspond » ; 404 → bouton masqué.
- Toutes les réussites renvoient le même objet que `/auth/login` : réutiliser le chemin existant de stockage de session et de redirection selon `user.userType` / rôles.
- Vérifier : connexion mot de passe, 5 échecs → 429, lien, code, Google, déconnexion révoque le jeton.

### Lot F2 — Paiement et espace apprenant (`feature/front-learner`)
Contrats : §3.D bis (paiement), §3.D quater (programme), §3.D quinquies (évaluations), **§3.D decies (vidéo)**, §3.D sexies (certificats), §3.D octies (questions).
- Page publique `/paiement/:token` (`Paiement.tsx` existe : l'aligner sur `GET /payments/{token}`, déclaration, capture ≤ 5 Mo, facture téléchargeable `GET /payments/{token}/invoice`, échéances, états expiré / déjà payé).
- `CoursePlayer` : si `videoEmbedUrl` présent → `<iframe src={videoEmbedUrl} allow="fullscreen; picture-in-picture" allowFullScreen referrerPolicy="strict-origin-when-cross-origin">` ; sinon `<video src={videoUrl}>`. Jamais d'iframe construite à partir de `videoUrl`.
- Espace apprenant : tableau de bord, cours, progression, quiz, projet final, évaluations, **mes certificats** (liste, PDF, lien de vérification, bouton « Ajouter à LinkedIn » fourni par l'API), **poser une question** depuis une leçon et relire les réponses.
- Page publique `/certificats/:publicId` (vérification) avec l'état « révoqué ».
- Respecter le contrôle d'accès du serveur (403/423 : accès non ouvert, quiz non réussi, ressource verrouillée) en affichant le message reçu.

### Lot F3 — Tableau de bord admin (`feature/front-admin-dashboard`)
Contrat : **§3.D nonies** (SUPER_ADMIN et ADMIN uniquement : masquer pour les autres rôles).
- Page « Tableau de bord » : sélecteur de période (défaut 30 jours, 366 max), cartes encaissé / net / reste à encaisser / en retard, entonnoir candidatures → acceptées → payées avec taux, courbe mensuelle, remplissage des sessions, top formations, répartitions (source, profil, pays, code promo), apprenants/certificats, newsletter/messages.
- Pastilles du menu admin alimentées par `GET /admin/stats/actions` (rafraîchissement toutes les 60 s, pause onglet masqué).
- Les montants sont en XOF : formater `150 000 FCFA`. Gérer les taux `null` (« — »).

### Lot F4 — Reste du back-office (`feature/front-admin-*`, à découper avec moi)
Contrats : §3.B, §3.C, §3.D bis, §3.D ter, §3.D quater/quinquies, §3.D sexies à octies, §3.E bis, §9.
À prévoir, dans cet ordre de valeur : Candidatures (7 statuts, **Accepter** → `/accept`, Refuser, **Inscription manuelle**, annulation, factures PDF, remboursement) ; Paiements (file « à confirmer », confirmer / refuser / relancer) ; Apprenants ; Sessions (formateur, suivi, appel des lives, certificats, messages de session, questions) ; éditeur de programme et banque de questions ; Domaines et Partenaires ; contenus du site (`site-settings`) ; journal des e-mails ; utilisateurs back-office. Proposer un découpage en 2 ou 3 branches avant de commencer.

## Rappels sur le comportement du backend

- Rôles : `SUPER_ADMIN`, `ADMIN`, `EDITOR`, `TRAINER`, `PARTNER`, `LEARNER` ; le jeton porte `roles` et `uty` (`ADMIN`/`LEARNER`). Un apprenant n'entre jamais dans `/admin/**` ; un formateur ne voit que ses sessions ; un partenaire que ses formations.
- Statuts d'inscription : `PENDING, PAYMENT_PENDING, PAYMENT_TO_CONFIRM, CONFIRMED, CANCELLED, COMPLETED, REJECTED`. Formats de formation : `PRESENTIEL / EN_LIGNE / HYBRIDE` (≠ sessions `PRESENTIEL / REMOTE / HYBRID`).
- Dates de live : heure locale du site (Africa/Dakar), sans décalage.
- Les liens de paiement, de vérification de certificat et de connexion sont à usage limité : ne pas les mettre en cache ni dans les logs.

## Ce que vous me rendez à chaque lot

Liste des fichiers créés/modifiés, ce qui a été vérifié dans le navigateur (et comment), les écarts de contrat constatés, le lien de PR.
