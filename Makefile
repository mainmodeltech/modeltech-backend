ifneq ("$(wildcard .env)","")
    include .env
    export $(shell sed 's/=.*//' .env)
endif

# ── Variables ────────────────────────────────────────────────
ENV_FILE       = .env
DOCKER_COMPOSE = docker-compose -f docker-compose.dev.yml --env-file $(ENV_FILE)
JAVA_HOME      = /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
MVNW           = JAVA_HOME=$(JAVA_HOME) ./mvnw

# ── Couleurs ─────────────────────────────────────────────────
RESET  = \033[0m
BOLD   = \033[1m
GREEN  = \033[32m
YELLOW = \033[33m
CYAN   = \033[36m
RED    = \033[31m

.PHONY: help \
	up down start stop restart logs ps \
	up-infra up-all \
	build run test clean \
	docker-build docker-run docker-stop \
	jwt-secret hash-password \
	db-connect db-reset

# ── Aide ─────────────────────────────────────────────────────
help:
	@echo ""
	@echo "$(BOLD)$(CYAN)╔══════════════════════════════════════════════════════╗$(RESET)"
	@echo "$(BOLD)$(CYAN)║       Model Technologie — Backend CLI                ║$(RESET)"
	@echo "$(BOLD)$(CYAN)╚══════════════════════════════════════════════════════╝$(RESET)"
	@echo ""
	@echo "$(BOLD)$(YELLOW)── Infrastructure Docker ──────────────────────────────$(RESET)"
	@echo "  $(GREEN)make up$(RESET)              Lancer infra (db, minio, adminer, dozzle)"
	@echo "  $(GREEN)make up-all$(RESET)          Lancer infra + backend dockerisé"
	@echo "  $(GREEN)make down$(RESET)            Arrêter tout et supprimer les volumes"
	@echo "  $(GREEN)make restart$(RESET)         Redémarrer toute la stack"
	@echo "  $(GREEN)make ps$(RESET)              Statut de tous les conteneurs"
	@echo "  $(GREEN)make logs$(RESET)            Logs en temps réel (tous les services)"
	@echo "  $(GREEN)make logs s=backend$(RESET)  Logs d'un service spécifique"
	@echo "  $(GREEN)make start s=db$(RESET)      Démarrer un service spécifique"
	@echo "  $(GREEN)make stop s=minio$(RESET)    Arrêter un service spécifique"
	@echo ""
	@echo "$(BOLD)$(YELLOW)── Backend Maven (dev local) ───────────────────────────$(RESET)"
	@echo "  $(GREEN)make run$(RESET)             Lancer Spring Boot en local (profil dev)"
	@echo "  $(GREEN)make build$(RESET)           Compiler et générer le JAR"
	@echo "  $(GREEN)make test$(RESET)            Exécuter les tests"
	@echo "  $(GREEN)make clean$(RESET)           Nettoyer le dossier target/"
	@echo ""
	@echo "$(BOLD)$(YELLOW)── Backend Docker ──────────────────────────────────────$(RESET)"
	@echo "  $(GREEN)make docker-build$(RESET)    Builder l'image Docker du backend"
	@echo "  $(GREEN)make docker-run$(RESET)      Lancer le backend dans Docker"
	@echo "  $(GREEN)make docker-stop$(RESET)     Arrêter le backend Docker"
	@echo ""
	@echo "$(BOLD)$(YELLOW)── Sécurité & Base de données ──────────────────────────$(RESET)"
	@echo "  $(GREEN)make jwt-secret$(RESET)      Générer un secret JWT fort (64 bytes)"
	@echo "  $(GREEN)make hash-password p=xxx$(RESET)  Hasher un mot de passe BCrypt"
	@echo "  $(GREEN)make db-connect$(RESET)      Ouvrir un shell psql sur la BDD"
	@echo "  $(GREEN)make db-reset$(RESET)        ⚠️  Supprimer et recréer la BDD"
	@echo ""


# ── Infrastructure Docker ─────────────────────────────────────

## Lancer uniquement l'infra (sans le backend)
up:
	@echo "$(CYAN)▶ Démarrage de l'infrastructure...$(RESET)"
	$(DOCKER_COMPOSE) up -d db minio adminer dozzle
	@echo "$(GREEN)✓ Infrastructure démarrée$(RESET)"
	@echo "  Adminer  → http://localhost:8081"
	@echo "  MinIO    → http://localhost:9001"
	@echo "  Dozzle   → http://localhost:8888"

## Lancer toute la stack (infra + backend dockerisé)
up-all:
	@echo "$(CYAN)▶ Démarrage de la stack complète...$(RESET)"
	$(DOCKER_COMPOSE) up --build
	@echo "$(GREEN)✓ Stack complète démarrée$(RESET)"
	@echo "  API      → http://localhost:8081"
	@echo "  Swagger  → http://localhost:8081/swagger-ui.html"
	@echo "  Adminer  → http://localhost:8081"
	@echo "  MinIO    → http://localhost:9001"
	@echo "  Dozzle   → http://localhost:8888"

## Arrêter tout et supprimer les volumes
down:
	@echo "$(RED)▼ Arrêt de tous les services...$(RESET)"
	$(DOCKER_COMPOSE) down -v
	@echo "$(GREEN)✓ Tous les services arrêtés$(RESET)"

## Démarrer un service spécifique — usage: make start s=db
start:
	$(DOCKER_COMPOSE) start $(s)

## Arrêter un service spécifique — usage: make stop s=minio
stop:
	$(DOCKER_COMPOSE) stop $(s)

## Redémarrer l'infra
restart:
	@echo "$(YELLOW)↺ Redémarrage de l'infrastructure...$(RESET)"
	$(DOCKER_COMPOSE) down -v
	$(DOCKER_COMPOSE) up -d db minio adminer dozzle
	@echo "$(GREEN)✓ Infrastructure redémarrée$(RESET)"

## Statut des conteneurs
ps:
	$(DOCKER_COMPOSE) ps

## Logs — usage: make logs  ou  make logs s=backend
logs:
ifdef s
	$(DOCKER_COMPOSE) logs -f $(s)
else
	$(DOCKER_COMPOSE) logs -f
endif


# ── Backend Maven (dev local) ─────────────────────────────────

## Lancer Spring Boot en local avec profil dev
## La BDD doit être up via: make up
run:
	@echo "$(CYAN)▶ Démarrage Spring Boot (profil dev)...$(RESET)"
	@echo "  API      → http://localhost:8081"
	@echo "  Swagger  → http://localhost:8081/swagger-ui.html"
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev

## Compiler et générer le JAR
build:
	@echo "$(CYAN)▶ Build Maven...$(RESET)"
	$(MVNW) clean install -DskipTests
	@echo "$(GREEN)✓ Build terminé$(RESET)"

## Exécuter les tests
test:
	@echo "$(CYAN)▶ Exécution des tests...$(RESET)"
	$(MVNW) test

## Nettoyer target/
clean:
	@echo "$(CYAN)▶ Nettoyage...$(RESET)"
	$(MVNW) clean
	@echo "$(GREEN)✓ target/ nettoyé$(RESET)"


# ── Backend Docker ────────────────────────────────────────────

## Builder uniquement l'image Docker du backend
docker-build:
	@echo "$(CYAN)▶ Build de l'image Docker backend...$(RESET)"
	$(DOCKER_COMPOSE) build backend
	@echo "$(GREEN)✓ Image construite$(RESET)"

## Lancer le backend dans Docker (infra doit être up)
docker-run:
	@echo "$(CYAN)▶ Démarrage du backend Docker...$(RESET)"
	$(DOCKER_COMPOSE) up -d backend
	@echo "$(GREEN)✓ Backend démarré$(RESET)"
	@echo "  API     → http://localhost:8081"
	@echo "  Swagger → http://localhost:8081/swagger-ui.html"

## Arrêter le backend Docker
docker-stop:
	$(DOCKER_COMPOSE) stop backend


# ── Sécurité & Utilitaires ────────────────────────────────────

## Générer un secret JWT fort (à copier dans .env → JWT_SECRET)
jwt-secret:
	@echo "$(CYAN)▶ Génération d'un secret JWT (64 bytes base64)...$(RESET)"
	@echo "$(YELLOW)Copie cette valeur dans .env → JWT_SECRET=$(RESET)"
	@openssl rand -base64 64 | tr -d '\n'
	@echo ""

## Hasher un mot de passe BCrypt — usage: make hash-password p=MonMotDePasse
hash-password:
ifndef p
	@echo "$(RED)✗ Usage: make hash-password p=MonMotDePasse$(RESET)"
	@exit 1
endif
	@echo "$(CYAN)▶ Hash BCrypt (strength 12)...$(RESET)"
	@docker run --rm alpine/openssl sh -c \
		"apk add --quiet bcrypt 2>/dev/null; echo '$p' | bcrypt" 2>/dev/null || \
	$(MVNW) -q exec:java \
		-Dexec.mainClass="org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder" \
		2>/dev/null || \
	echo "$(YELLOW)→ Utilise Swagger POST /api/v1/auth/login pour tester directement$(RESET)"

## Ouvrir un shell psql sur la BDD
db-connect:
	@echo "$(CYAN)▶ Connexion à la BDD...$(RESET)"
	docker exec -it modeltech_db psql -U $(DB_USER) -d $(DB_NAME)

## ⚠️  Supprimer et recréer la BDD (DESTRUCTIF)
db-reset:
	@echo "$(RED)⚠️  ATTENTION : Cette commande supprime toutes les données !$(RESET)"
	@read -p "Confirmer ? (oui/non) : " confirm && [ "$$confirm" = "oui" ] || exit 1
	$(DOCKER_COMPOSE) down -v
	$(DOCKER_COMPOSE) up -d db
	@echo "$(GREEN)✓ BDD réinitialisée — Flyway va rejouer toutes les migrations au prochain démarrage$(RESET)"
