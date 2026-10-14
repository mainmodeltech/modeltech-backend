# Phase 3a — Profils et droits

Chaque personne de l'équipe ne voit et ne fait que ce que son métier exige. Les accès sont des **droits** regroupés en **profils** ; une personne peut cumuler plusieurs profils (le responsable administratif et financier a « Finance » + « Admissions »). La direction règle les droits de chaque profil depuis **Back-office → Profils & droits** et peut créer des profils sur mesure.

## Profils livrés

| Profil | Pour qui | Ce qu'il peut faire (en bref) |
|---|---|---|
| **Direction** (`ROLE_SUPER_ADMIN`) | gérant, adjoint de confiance | Tout, y compris comptes, profils, journal. Les droits ajoutés plus tard lui reviennent automatiquement. Garder **au moins deux** comptes. |
| **Finance** | responsable administratif et financier | Paiements (confirmer, refuser, rembourser), factures, codes de parrainage, **tarifs**, **parts de revenu des partenaires**, déroger au prix, tableau de bord financier. Voit les candidatures, apprenants, catalogue, certificats. |
| **Admissions** | commerciale, responsable administratif | Candidatures (accepter, refuser, annuler), inscriptions manuelles, apprenants, messages entrants, journal d'e-mails ; voit le **statut** des paiements sans pouvoir les modifier ; tableau d'activité **sans montants**. |
| **Pédagogie** | responsable pédagogique | Formations, programmes, sessions (suivi, appel, affectation des formateurs), **certificats** (y compris dérogation), communauté, partenaires (sans la part de revenu), tableau d'activité. |
| **Communication** | community manager | Site, témoignages, alumni, projets, newsletter, messages, masterclass, communauté. |
| **Formateur** | les 3 formateurs | Animer **ses** sessions uniquement : appel, correction des projets, réponses, messages. |
| **Partenaire** | partenaire externe | Le programme de **ses** formations uniquement. |
| **Lecture seule / Audit** | expert-comptable, associé | Voit finances et journal, ne modifie rien. |
| Gestion complète (transition) | ancien « Administrateur » | Tout sauf comptes, profils, système et audit. À remplacer par les profils par métier. |
| Éditeur (ancien profil) | ancien « Éditeur » | Catalogue, programmes, site, messages, newsletter. À remplacer. |

> Le détail exact de chaque profil est dans l'écran **Profils & droits** (cartes et **matrice** pour la revue trimestrielle) et dans `DefaultProfiles.java`.

## Règles de sécurité

- **Refus par défaut** : une route `/api/v1/admin/**` sans règle est fermée, même pour la direction. Toutes les règles sont dans `AdminAccessRules.java` ; un test (`AdminAccessRulesIT`) échoue si une route d'administration existe sans règle.
- **Aucune élévation** : on ne peut accorder à un profil, ni attribuer à un compte, que des droits qu'on possède soi-même ; seule la direction attribue « Direction ».
- **Droits sensibles séparés** : tarifs (`PRICING_MANAGE`), part de revenu partenaire (`PARTNERS_REVENUE`), montant différent du tarif à l'acceptation (`PRICE_OVERRIDE`), certificat par dérogation (`CERTIFICATES_OVERRIDE`) ne sont pas implicites à l'édition du catalogue ou à l'acceptation d'une candidature. Un formulaire qui renvoie un prix inchangé reste accepté ; le droit « Tarifs » seul ne retient que les prix.
- **Montants** : sans `DASHBOARD_FINANCE`, les montants sont **absents** de la réponse du tableau de bord (pas seulement masqués à l'écran). Les pastilles du menu ne comptent que les files que la personne traite.
- Les droits sont relus en base **à chaque requête** : une modification s'applique immédiatement côté serveur ; le menu se met à jour à la reconnexion (au plus 15 minutes).

## Mise en production

1. Déployer le backend (migration `V33` : table `role_permissions`, colonnes `roles.label/is_system/is_staff`, 5 nouveaux profils). **Aucun compte existant n'est modifié** : les `ADMIN` et `EDITOR` gardent leur périmètre actuel.
2. Dans **Comptes back-office**, attribuer à chaque personne ses profils par métier, puis retirer « Gestion complète (transition) » / « Éditeur ». Vérifier avec la matrice des droits.
3. Déployer le front (menu et boutons suivent les droits).
4. Rôle des formateurs : sans le droit « Affecter un formateur », ils ne voient que les sessions qui leur sont confiées.

## Lots suivants (réalisés)

- **3b Journal d'audit** et **3c Séparation des tâches** : voir [phase-3b-3c-audit-et-quatre-yeux.md](phase-3b-3c-audit-et-quatre-yeux.md).
