ifneq ("$(wildcard .env)","")
    include .env
    export $(shell sed 's/=.*//' .env)
endif

# Variables
ENV_FILE=.env
DOCKER_COMPOSE=docker-compose --env-file $(ENV_FILE)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
MVNW=JAVA_HOME=$(JAVA_HOME) ./mvnw

.PHONY: help up down start stop run build test clean

# Aide par défaut
help:
	@echo "Commandes disponibles :"
	@echo "  make up         : Lancer tous les services Docker (db, minio, adminer, dozzle)"
	@echo "  make down       : Arrêter tous les services et supprimer les volumes"
	@echo "  make start s=<nom> : Lancer un service spécifique (ex: make start s=db)"
	@echo "  make stop s=<nom>  : Arrêter un service spécifique (ex: make stop s=minio)"
	@echo "  make build      : Compiler le projet et générer le JAR"
	@echo "  make run        : Lancer l'application Spring Boot (profil dev)"
	@echo "  make test       : Exécuter les tests unitaires"
	@echo "  make clean      : Nettoyer le dossier target"

# --- Docker Services ---

up:
	$(DOCKER_COMPOSE) up -d

down:
	$(DOCKER_COMPOSE) down -v

# Usage: make start s=db
start:
	$(DOCKER_COMPOSE) start $(s)

# Usage: make stop s=db
stop:
	$(DOCKER_COMPOSE) stop $(s)

restart:
	$(DOCKER_COMPOSE) down -v
	$(DOCKER_COMPOSE) up -d
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev

# --- Backend Maven ---

build:
	$(MVNW) clean install -DskipTests

run:
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev

test:
	$(MVNW) test

clean:
	$(MVNW) clean