# Phase 0 — Urgences d'infrastructure (staging et production)

Suite de l'audit de sécurité du 11 octobre 2026 (`data-mastery-hub/docs/security/audit-securite-et-architecture.md`).
Ce lot corrige l'exposition de l'infrastructure ; il ne change aucune fonctionnalité visible.

## 1. Ce que ce lot change dans le dépôt

| Constat | Correction |
|---|---|
| **C1** Bucket MinIO public en lecture, liste, écriture et suppression (`mc anonymous set public`), contenant des justificatifs de paiement | Politique **lecture seule** (`s3:GetObject`) limitée à `alumni/`, `partners/`, `projects/screenshots/`, `projects/covers/` (`scripts/minio/apply-public-policy.sh`, `set-public.sh`, service `minio-init-staging`). Pas de liste, pas d'écriture, pas de suppression anonymes. Testée sur un bucket réel : voir §4. |
| **C1 bis** Les justificatifs de paiement (déposés par le détenteur du lien, sans compte) étaient enregistrés avec une URL publique | `StorageService.uploadPrivateImage` : contenu vérifié sur les premiers octets (JPEG/PNG/WebP/GIF — un HTML ou un SVG déguisé est refusé), 5 Mo max, stocké en `application/octet-stream`, **aucune URL publique en base**. Le back-office reçoit un **lien signé valable 15 minutes** à chaque consultation (`PaymentMapper`), y compris pour les anciens justificatifs. |
| **C2** Adminer et Dozzle (socket Docker) déployés avec la stack | Retirés de `docker-compose.yml` et `docker-compose.staging.yml`. Disponibles à la demande dans `docker-compose.tools.yml`, uniquement sur `127.0.0.1` du serveur (tunnel SSH). |
| **M1** Swagger ouvert et journaux DEBUG en staging | `application-staging.yml` : springdoc désactivé, niveaux INFO / SQL en WARN. |
| Sauvegardes | `scripts/backup/pg-backup.sh` (chiffrée AES-256, vérifiée, rétention) et `scripts/backup/pg-restore-check.sh` (restauration dans une base temporaire). |

Aucun changement du front n'est nécessaire : `proofUrl` garde le même nom, il contient seulement un lien signé.

## 2. À faire sur le serveur (je n'y ai pas accès) — dans cet ordre

Les adresses `dozzle.`, `adminer.` et `minio-staging.model-technologie.com` étaient publiques. **Considérez que les données accessibles par ces outils ont pu être lues.**

1. **Couper l'accès sans attendre le déploiement** : dans Dokploy, supprimer les domaines `dozzle.model-technologie.com` et `adminer.model-technologie.com` (et arrêter les deux conteneurs). Vérifier aussi s'il existe un domaine pour la console MinIO (`MINIO_CONSOLE_DOMAIN`, port 9001) : le supprimer ou le restreindre à votre IP.
2. **Déployer ce lot**, puis appliquer la politique du bucket :
   - staging : le service `minio-init-staging` la réapplique à chaque déploiement ;
   - production (pas de service d'initialisation) : `bash set-public.sh` à la racine du dépôt sur le VPS.
3. **Contrôler le bucket** (liste depuis la console MinIO ou `mc ls`) : repérer tout objet inconnu (fichiers déposés par un tiers) et le supprimer.
4. **Faire tourner les secrets** (précaution : Adminer permettait d'essayer des mots de passe, Dozzle montrait les journaux) :
   - mot de passe PostgreSQL : `ALTER USER <utilisateur> PASSWORD '...'` dans la base **et** `DB_PASSWORD` dans Dokploy (la variable `POSTGRES_PASSWORD` ne s'applique qu'à la création du volume) ;
   - `JWT_SECRET` (`openssl rand -base64 64`) : déconnecte tout le monde, c'est voulu ;
   - `MINIO_ROOT_PASSWORD` (et `MINIO_ROOT_USER` si possible) ;
   - clés de service tierces si vous avez vu un secret dans un journal : `MAIL_PASSWORD`, webhook Slack.
5. **Rechercher des traces** : journaux d'accès Traefik / Dokploy pour ces trois noms d'hôte (requêtes inhabituelles, `PUT`, `DELETE`), table `admin_users` (comptes créés récemment que vous ne reconnaissez pas : `select email, created_at from admin_users order by created_at desc;`), connexions PostgreSQL inhabituelles.
6. **Mettre en place les sauvegardes** (§3).

## 3. Sauvegardes de la base

Une fois, sur le VPS :

```bash
sudo install -d -m 700 /var/backups/modeltech
openssl rand -base64 48 | sudo tee /root/.modeltech-backup-passphrase >/dev/null
sudo chmod 600 /root/.modeltech-backup-passphrase
```

**Conservez cette phrase secrète en deux endroits hors du serveur (gestionnaire de mots de passe + coffre de l'entreprise) : sans elle, les sauvegardes sont inutilisables.**

Sauvegarde quotidienne (cron root, 02:30) :

```bash
30 2 * * * cd /chemin/du/depot && DB_CONTAINER=modeltech_prod_db DB_NAME=<nom> DB_USER=<utilisateur> BACKUP_DIR=/var/backups/modeltech BACKUP_PASSPHRASE_FILE=/root/.modeltech-backup-passphrase bash scripts/backup/pg-backup.sh >> /var/log/modeltech-backup.log 2>&1
```

Test de restauration (après la première sauvegarde, puis chaque trimestre) :

```bash
DB_CONTAINER=modeltech_prod_db DB_USER=<utilisateur> BACKUP_PASSPHRASE_FILE=/root/.modeltech-backup-passphrase \
  bash scripts/backup/pg-restore-check.sh /var/backups/modeltech/<fichier>.dump.enc
```

Il restaure dans une base temporaire `restore_check_*`, affiche le nombre de tables, la dernière migration Flyway et quelques effectifs, puis supprime la base temporaire. Il n'écrit jamais dans la base réelle.

**Reste à décider** : la copie hors serveur (une sauvegarde qui reste sur le même VPS ne protège pas d'une perte du VPS). Option simple : `rclone sync /var/backups/modeltech <bucket-S3-d'un-autre-fournisseur>` après la sauvegarde. Les fichiers sont déjà chiffrés. Les **fichiers MinIO** (logos, justificatifs, rendus) ne sont pas couverts ici : à planifier avec la même copie externe (`mc mirror`).

## 4. Vérifications

Réalisées pour ce lot (poste local, MinIO de développement, bucket jetable) :

| Requête anonyme | Résultat attendu | Obtenu |
|---|---|---|
| `GET` `alumni/`, `partners/`, `projects/screenshots/`, `projects/covers/` | 200 | 200 |
| `GET` `payment-proofs/…` | 403 | 403 |
| `GET` `projects/<id-formation>/…` (rendus d'apprenants) | 403 | 403 |
| Liste du bucket | 403 | 403 |
| `PUT` / `DELETE` | 403 | 403 (objet toujours présent) |
| `GET` avec lien signé sur un objet privé | 200 | 200 |

Tests automatisés : `StoragePrivateImageTest` (3), `StorageDocumentUploadTest` (3), `PaymentFlowIT` (23, dont dépôt d'un justificatif privé et lien signé côté admin), `HardeningIT` (4), `AdminPartnerControllerIT` (7). Sauvegarde et restauration testées sur un clone de la base de développement (53 tables, migration V31, base temporaire supprimée).

À faire par vous après déploiement, **depuis un poste extérieur au serveur** :

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://minio-staging.model-technologie.com/media/?list-type=2        # 403 attendu
curl -s -o /dev/null -w "%{http_code}\n" -X PUT -d x https://minio-staging.model-technologie.com/media/test.txt # 403 attendu
curl -s -o /dev/null -w "%{http_code}\n" https://adminer.model-technologie.com/                                 # plus de réponse (404 / erreur de connexion)
curl -s -o /dev/null -w "%{http_code}\n" https://dozzle.model-technologie.com/                                  # idem
curl -s -o /dev/null -w "%{http_code}\n" https://<domaine-api-staging>/swagger-ui.html                           # 404 / 401
```

## 5. Limites connues de ce lot

- Les images **publiques** (`alumni`, `partners`, `projects/*`) restent validées par l'en-tête du client ; le contrôle du contenu réel et le refus du SVG sont prévus en phase 1 (constat M11).
- L'ancienne colonne `payments.proof_url` n'est plus alimentée pour les nouveaux dépôts ; les anciennes valeurs restent en base mais ne sont plus renvoyées dès que la clé de l'objet est connue.
- Les images Docker ne sont pas encore épinglées (phase 1).
