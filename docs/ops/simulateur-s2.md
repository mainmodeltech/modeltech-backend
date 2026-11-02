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

---

# Lot S2b — essai gratuit (produit d'appel)

## Principe
Un visiteur laisse nom + e-mail sur la page publique ; un compte apprenant **sans mot de passe** est créé, un droit « Essai » (1 mini-test, 7 jours) lui est accordé et il reçoit un **lien de connexion à usage unique** (valable 10 min). Se connecter avec ce lien vérifie son adresse. La demande apparaît dans *Messages* (type « Essai gratuit simulateur », filtre « Simulateur »).

## Ce que voit un candidat en essai
- **Mini-test** : 10 questions maximum, 15 minutes, **une seule tentative**, composé **uniquement des questions « échantillon »** (toujours les mêmes, jamais tirées dans toute la banque).
- **Pas d'entraînement libre** (403) et la taille de la banque n'est pas divulguée.
- **Rapport** : pourcentage (pas de note /1000 ni « réussi »), résultats par domaine et compétence, **3 corrections détaillées** (les premières erreurs). Les autres questions sont **verrouillées côté serveur** : ni corrigé, ni explication, ni note de la question.
- Dès qu'un forfait payant est accordé, le candidat retrouve le simulateur complet.

## Sécurité
- Route publique `POST /api/v1/simulator/trial` : limitée par IP, **même réponse** que l'adresse soit neuve, connue, d'un compte du back-office ou déjà utilisée (pas d'énumération de comptes) ; adresses jetables refusées.
- **Un seul essai par personne**, garanti par la base (index unique partiel `uq_sim_one_trial_per_learner`) et par le service.
- Les échantillons se choisissent dans *Simulateur → Questions* (droit `SIMULATOR_MANAGE`, uniquement une question **publiée**, tracé : `SIM_TRIAL_SAMPLE_SET`). La création de comptes à la chaîne ne permet donc d'aspirer que ce petit lot.

## Configuration (valeurs par défaut)
| Clé | Défaut | Rôle |
|---|---|---|
| `app.simulator.trial-questions` | 10 | Taille maximale du mini-test |
| `app.simulator.trial-minutes` | 15 | Durée |
| `app.simulator.trial-days` | 7 | Validité du droit « Essai » |
| `app.simulator.trial-corrections` | 3 | Corrections détaillées offertes |

## Mise en production
1. Déployer le backend (migration **V43**, automatique).
2. Marquer **au moins 10 questions publiées** comme échantillon (représentatives de l'examen, **pas les meilleures de la banque**, couvrant les 4 domaines). Sans échantillon, l'essai répond « pas encore disponible » et ne consomme rien.
3. *Pages du site* → **Menu & pied de page** puis **Simulateur PL-300** → **Restaurer** : les contenus déjà enregistrés ne reprennent pas seuls les nouveaux textes par défaut (menu « Simulateur PL-300 » à la place de « Ressources », bandeau et section d'essai).
4. Vérifier l'envoi des e-mails de connexion (domaine d'expédition) avant d'ouvrir l'essai au public.

---

# Lot S2c — questions « Correspondance » et « Réponse libre »

Deux nouveaux types de questions de l'examen PL-300 (migration **V44**, automatique) :

| Type | Principe | Note |
|---|---|---|
| `MATCHING` (glisser-déposer) | 2 à 6 éléments à associer à 2 à 8 options (leurres possibles), chaque option une seule fois sauf `reuse` | part d'éléments correctement associés |
| `SHORT_ANSWER` (réponse libre) | le candidat rédige ; la réponse doit contenir **tous** les mots-clés (1 à 8, plusieurs formulations chacun) | tout ou rien, ou proportionnelle (`partialCredit`) |

## Règles de reconnaissance des mots-clés
Casse et accents ignorés ; ponctuation = espace ; un mot-clé est reconnu **au début d'un mot** (« mesure » reconnaît « mesures », « pivot » ne reconnaît pas « unpivot ») ; une expression doit apparaître avec ses mots contigus. Aucune expression régulière n'est construite à partir du texte du candidat ; la réponse est limitée à `maxLength` (20 à 2000, 600 par défaut).

## Sécurité
- La vue du candidat ne contient **ni les associations attendues, ni les mots-clés** : seulement les éléments, les options (**mélangées à chaque affichage**) ou la taille maximale de la réponse (tests unitaires dédiés).
- Le détail de correction (mots-clés trouvés ou manquants) n'est servi qu'après la remise, ou question par question en mode étude.
- Les définitions sont revalidées par le serveur à la création, à la modification et à l'import (message précis : élément sans option attendue, option attendue deux fois, mot-clé sans formulation…).
- Dans l'essai gratuit, les corrections de ces types sont verrouillées comme les autres.

## Mise en production
Déployer le backend (V44). Aucune action manuelle. Les questions existantes ne changent pas.
