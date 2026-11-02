# Simulateur PL-300 — lot S2 : moteur de tentative et droits d'usage

## Ce que le lot apporte
- **Droits d'usage** nominatifs (`sim_access_grant`) : forfait, période, quota d'examens blancs. Octroyés **à la main** par la Pédagogie tant que le paiement en ligne (lot S3) n'existe pas ; chaque octroi/révocation est tracé (`SIM_ACCESS_GRANTED`, `SIM_ACCESS_REVOKED`).
- **Examen blanc** : composé par le serveur selon le blueprint (50 questions → 14/14/14/8 par domaine), 100 minutes, **échéance décidée et contrôlée par le serveur**, une seule tentative d'examen en cours par apprenant.
- **Mode étude** : par domaine, correction immédiate après chaque réponse, sans quota.
- **Notation** côté serveur (Strategy par type de question), score estimé sur 1 000 (seuil 700), rapport par domaine et compétence avec corrigé et explication FR **après** remise.

## API
| Route | Rôle |
|---|---|
| `GET /api/v1/learner/simulator/catalog` | Droits, quotas, domaines, tentative en cours |
| `GET/POST /api/v1/learner/simulator/attempts` | Historique / démarrer (`EXAM` ou `STUDY`) |
| `GET /attempts/{id}` | Reprendre (questions **sans corrigé**) |
| `PUT /attempts/{id}/answers/{ref}` | Enregistrer (idempotent) ; correction renvoyée **uniquement en étude** |
| `POST /attempts/{id}/submit` | Remettre (idempotent) |
| `GET /attempts/{id}/report` | Rapport (409 tant que la tentative n'est pas terminée) |
| `GET/POST /api/v1/admin/simulator/grants`, `POST …/{id}/revoke` | Droits d'usage (`SIMULATOR_MANAGE`) |

## Règles garanties
- Aucun champ de correction dans les réponses d'une tentative en cours (test d'intégration qui parcourt le JSON).
- Tentative d'un autre apprenant → **404** (jamais 403, pour ne pas révéler son existence).
- Réponse après l'échéance (+ 5 s de marge) → **409**, la tentative est remise et notée automatiquement avec ce qui avait été enregistré.
- Un examen abandonné **compte** dans le quota ; un échec d'assemblage (banque insuffisante) **ne consomme rien**.
- Quota consommé par un `UPDATE` conditionnel atomique : deux démarrages simultanés ne dépassent pas le quota.

## Configuration (`application.yml`, valeurs par défaut)
| Clé | Défaut | Rôle |
|---|---|---|
| `app.simulator.exam-questions` | 50 | Questions d'un examen blanc |
| `app.simulator.grace-seconds` | 5 | Marge réseau après l'échéance |
| `app.simulator.study-default` / `study-max` | 10 / 30 | Taille d'une série d'étude |
| `app.simulator.sweep-ms` | 60000 | Période du balayage des examens expirés |

## Mise en production
1. Déployer le backend (migration **V42**, automatique).
2. Publier assez de questions : un examen de 50 questions exige au moins la répartition du blueprint (14/14/14/8) ; sinon l'apprenant reçoit un message clair et aucun essai n'est consommé.
3. Octroyer les droits aux testeurs (*Simulateur → Droits d'usage*) — l'apprenant doit avoir un compte.

## Reporté au lot S4 (durcissement anti-fuite)
Ordre des choix aléatoire par tentative, filigrane, détection de lecture massive, statistiques de qualité des questions.
