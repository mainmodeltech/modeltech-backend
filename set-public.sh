#!/bin/bash
# Applique au bucket MinIO la lecture anonyme LIMITEE aux dossiers d'images publiques
# (voir scripts/minio/apply-public-policy.sh). Ne rend plus le bucket entier public.
# Executer depuis le VPS (a la racine du depot) avec : bash set-public.sh
set -euo pipefail

BUCKET="${MINIO_BUCKET:-media}"
MINIO_USER="${MINIO_ROOT_USER:?MINIO_ROOT_USER manquant}"
MINIO_PASS="${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD manquant}"
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "→ Recherche du conteneur MinIO..."
CONTAINER=$(docker ps --format "{{.Names}}" | grep -i minio | grep -vi "console\|init" | head -1)
echo "  Conteneur : $CONTAINER"

echo "→ Configuration de l'alias mc..."
docker exec "$CONTAINER" mc alias set local http://localhost:9000 "$MINIO_USER" "$MINIO_PASS" --quiet

echo "→ Application de la politique de lecture limitee sur '$BUCKET'..."
docker exec -i "$CONTAINER" sh -s -- local "$BUCKET" < "$HERE/scripts/minio/apply-public-policy.sh"

echo "✅ Termine : seuls alumni/, partners/, projects/screenshots/ et projects/covers/ sont lisibles anonymement."
