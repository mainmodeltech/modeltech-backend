# Model Technology - Data Mastery Hub Backend

Backend robuste en Java Spring Boot pour la gestion des formations, des alumni et du contenu du Hub.

## 🚀 Stack Technique
- **Framework**: Spring Boot 3.4.1 (Java 21)
- **Base de données**: PostgreSQL
- **Migrations**: Flyway
- **Stockage**: MinIO (S3 compatible)
- **Documentation**: Swagger/OpenAPI
- **Logs & Admin**: Dozzle & Adminer
- **Déploiement**: Docker & Dokploy

## 🛠 Installation & Lancement (Dev)

1. **Prérequis**: Docker, Java 21, Maven.
2. **Setup Environnement**: Copier `.env.example` vers `.env.dev` et remplir les valeurs.
3. **Lancer l'infrastructure**:
   ```bash
   docker-compose up -d