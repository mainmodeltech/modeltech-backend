# ============================================================
# Dockerfile — Model Technologie Backend
# Multi-stage build : build Maven → image JRE légère
# ============================================================

# ── Stage 1 : Build ─────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copier les fichiers Maven en premier pour profiter du cache Docker
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Télécharger les dépendances (mise en cache si pom.xml n'a pas changé)
RUN ./mvnw dependency:go-offline -B

# Copier les sources et builder
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ── Stage 2 : Run ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS runtime

WORKDIR /app

# Sécurité : utilisateur non-root
RUN groupadd -r modeltech && useradd -r -g modeltech modeltech

# Copier uniquement le JAR final
COPY --from=build /app/target/*.jar app.jar

# Changer le propriétaire
RUN chown modeltech:modeltech app.jar

USER modeltech

# Port exposé
EXPOSE 8085

# Health check intégré
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# Démarrage avec options JVM optimisées pour conteneur
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]