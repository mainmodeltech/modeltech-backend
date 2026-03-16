#!/bin/bash
# Rendre le bucket MinIO public en lecture anonyme
# Exécuter depuis le VPS avec : bash set-public.sh

BUCKET="${MINIO_BUCKET:-media}"
MINIO_USER="${MINIO_ROOT_USER}"
MINIO_PASS="${MINIO_ROOT_PASSWORD}"

echo "→ Connexion au container MinIO..."

# Trouve le nom du container minio
CONTAINER=$(docker ps --format "{{.Names}}" | grep -i minio | grep -v console | head -1)
echo "  Container: $CONTAINER"

echo "→ Configuration mc alias..."
docker exec "$CONTAINER" mc alias set local http://localhost:9000 "$MINIO_USER" "$MINIO_PASS" --quiet

echo "→ Passage du bucket '$BUCKET' en accès public..."
docker exec "$CONTAINER" mc anonymous set public "local/$BUCKET"

echo "→ Vérification..."
docker exec "$CONTAINER" mc anonymous get "local/$BUCKET"

echo "✅ Done. Les fichiers dans '$BUCKET' sont maintenant accessibles publiquement."