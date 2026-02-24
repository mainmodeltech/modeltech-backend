#!/bin/bash

# Configuration du package de base
BASE_PACKAGE="src/main/java/com/modeltech/datamasteryhub"

# Création des dossiers de configuration et core
mkdir -p $BASE_PACKAGE/{config,security,exception,common/audit,common/persistence,modules}

# Création des modules métier (chaque module aura sa propre structure interne)
MODULES=("identity" "training" "networking" "cms" "communication" "storage")

for module in "${MODULES[@]}"
do
    mkdir -p $BASE_PACKAGE/modules/$module/{controller,service/impl,repository,entity,dto/request,dto/response,mapper}
done

# Création des dossiers de ressources
mkdir -p src/main/resources/{db/migration,i18n}

# Création des fichiers de base (Placeholders)
touch $BASE_PACKAGE/common/persistence/BaseEntity.java
touch $BASE_PACKAGE/common/persistence/SoftDeleteRepository.java
touch $BASE_PACKAGE/exception/GlobalExceptionHandler.java
touch $BASE_PACKAGE/exception/ErrorResponse.java
touch src/main/resources/application-{dev,staging,prod}.yml

echo "✅ Structure du projet Spring Boot créée avec succès !"
