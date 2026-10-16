# Phases 3b et 3c — journal d'audit et séparation des tâches

## Ce qui change

**3b — Journal d'audit.** Chaque action sensible écrit une ligne dans `audit_log` : auteur (e-mail, nom, profils), horodatage, action, élément concerné, résumé lisible, détails (JSON), marquage éventuel et adresse IP. Consultation : `GET /api/v1/admin/audit` (droit `AUDIT_VIEW`, filtres `category`, `action`, `actor`, `flag`, `q`, `from`, `to`, pagination) et `GET /api/v1/admin/audit/catalogue`. Aucune route d'écriture.

- **Ajout seul** : un déclencheur PostgreSQL (`audit_log_immutable`) refuse tout `UPDATE` et `DELETE`, même pour le compte applicatif.
- **Même transaction que l'action** (`AuditService.record`, `Propagation.REQUIRED`) : action annulée = trace annulée ; trace impossible = action impossible.
- **Auteur et IP relevés par le système** depuis la session et la requête, jamais fournis par l'appelant.
- Actions couvertes : voir l'énumération `AuditAction` (paiements, remboursements, candidatures, factures, prix, parrainage, parts de revenu, certificats, tentatives de quiz, comptes, profils, contenu du site, apprenants). Ajouter une action = une valeur d'énumération + un appel dans le service.
- Marquages : `NO_SECOND_CHECK` (« Sans second contrôle ») et `OVERRIDE` (« Dérogation »).

**3c — Séparation des tâches.**

| Action | Règle |
|---|---|
| Remboursement | `POST /payments/{id}/refund` = **demande** (`PAYMENTS_REFUND`). `POST /payments/{id}/refund/approve` et `/refund/decline` (`PAYMENTS_REFUND_APPROVE`). Le demandeur ne valide pas sa propre demande. |
| Paiement hors site | `POST /registrations/{id}/payments` (`PAYMENTS_RECORD`, nouveau droit — Admissions et Finance) saisit ; `POST /payments/{id}/confirm` (`PAYMENTS_MANAGE`) confirme. Celui qui a saisi ne confirme pas. |
| Paiement déclaré par l'apprenant | Une seule personne le confirme, sans contrainte. |
| Dérogations (prix, certificat) | Pas de second validateur : tracées avec le marquage `OVERRIDE`. |

**Repli petite équipe** (`FourEyes.otherHolderExists`) : si aucune **autre** personne habilitée n'existe, l'action est exécutée et marquée `NO_SECOND_CHECK`. Est « habilitée » un compte **actif**, non supprimé, **connecté dans les `app.four-eyes.active-days` derniers jours (90 par défaut)** et portant le droit (la Direction compte pour tous les droits). Un compte oublié ou jamais utilisé (ancien compte de démarrage) ne bloque donc pas la validation.

## Migration V38

Table `audit_log` + déclencheur ; colonnes `payments.recorded_by`, `confirmed_unchecked`, `refund_requested_at/by`, `refund_request_reason`, `refund_unchecked` ; nouveaux droits `PAYMENTS_RECORD` (profils qui avaient `PAYMENTS_MANAGE`, plus Admissions) et `PAYMENTS_REFUND_APPROVE` (profils qui avaient `PAYMENTS_REFUND`).

## Configuration

| Propriété | Défaut | Rôle |
|---|---|---|
| `app.four-eyes.active-days` | 90 | Ancienneté maximale de la dernière connexion d'un compte pour compter comme second validateur |

## Mise en production

1. Déployer le backend (V38 appliquée au démarrage). **Aucune action manuelle.**
2. Vérifier la matrice dans **Profils & droits** : un profil **personnalisé** qui avait `PAYMENTS_MANAGE` / `PAYMENTS_REFUND` a reçu les nouveaux droits par la migration ; vérifier qu'Admissions peut saisir un paiement hors site mais pas le confirmer.
3. **Vérifier qu'au moins deux personnes** détiennent `PAYMENTS_REFUND_APPROVE` et `PAYMENTS_MANAGE` dès que l'équipe le permet ; tant que ce n'est pas le cas, les actions sont autorisées et marquées « Sans second contrôle ».
4. Désactiver les comptes inutilisés (ancien compte de démarrage) : ils ne bloquent pas le repli, mais augmentent la surface d'attaque.
5. Déployer le front (journal d'audit dans le menu *Équipe & comptes* ; boutons de demande / validation de remboursement ; saisie hors site pour Admissions).
6. Désigner la personne qui lit le journal chaque semaine (filtre « À surveiller »).

## Limites connues

- Les **exports** (CSV) et les **actions automatiques** du système (relances, préparation des certificats) ne sont pas consignés.
- Le journal n'a pas de purge : volume faible (une ligne par action sensible). Prévoir une revue de la taille de la table à 12 mois ; ne **jamais** supprimer des lignes sans décision de la Direction (le déclencheur l'interdit : un archivage passerait par une procédure dédiée).
- Un remboursement consigné **ne déclenche pas** le virement : il est effectué hors plateforme.

## Reste à faire

- Exploiter le journal : alerte hebdomadaire par e-mail à la Direction des lignes marquées (à décider).
- Étendre la validation à d'autres actions si besoin (changement de prix, révocation d'un certificat).
