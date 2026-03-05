# Backend — Spring Boot API

## Tech

Spring Boot 3.2.5, Java 17 (Temurin), PostgreSQL 15, Flyway, JWT, MapStruct, Lombok, MinIO.

## Package racine

`com.modeltech.datamasteryhub`

## Architecture

```
src/main/java/.../
  common/persistence/
    BaseEntity.java          — audit fields (createdAt, updatedAt, isDeleted, etc.)
    SoftDeleteRepository.java — @NoRepositoryBean, methodes findByIdAndIsDeletedFalse, etc.
  config/
    AsyncConfig.java         — @EnableAsync, ThreadPoolTaskExecutor
    JpaConfig.java           — auditing
    OpenApiConfig.java       — Swagger
  exception/
    GlobalExceptionHandler.java — @ControllerAdvice
    ResourceNotFoundException.java
  security/
    SecurityConfig.java      — JWT + CORS (origines via @Value)
    JwtTokenProvider.java
    JwtAuthenticationFilter.java
  modules/
    auth/          — AdminUser entity, login JWT
    training/      — Bootcamp, BootcampSession, Registration
    cms/           — Service (prestations B2B)
    communication/ — ContactMessage
    notification/  — SlackNotifier, EmailNotifier (async)
```

## Pattern par module

```
controller/
  PublicXxxController.java    — endpoints publics (permitAll)
  AdminXxxController.java     — endpoints admin (/api/v1/admin/...)
dto/
  request/CreateXxxRequest.java
  request/UpdateXxxRequest.java
  response/XxxResponse.java
entity/Xxx.java               — extends BaseEntity, @Getter @Setter @NoArgsConstructor
enums/XxxStatus.java
mapper/XxxMapper.java         — MapStruct (@Mapper componentModel="spring")
repository/XxxRepository.java — extends SoftDeleteRepository<Xxx, UUID>
service/XxxService.java       — interface
service/impl/XxxServiceImpl.java — @Service @Transactional(readOnly=true)
```

## Regles importantes

- **Pas de @Builder sur les entities** : Lombok @Builder ne gere pas les champs herites de BaseEntity → MapStruct echoue. Utiliser @Getter @Setter @NoArgsConstructor.
- **MapStruct** : ignorer les champs de BaseEntity dans toEntity() (`@Mapping(target = "createdAt", ignore = true)`, etc.)
- **Soft delete** : BaseEntity fournit isDeleted, deletedAt, deletedBy. Ne jamais faire de hard delete.
- **UUID** : tous les IDs sont UUID (`@GeneratedValue(strategy = GenerationType.UUID)`)
- **NullValuePropertyMappingStrategy.IGNORE** pour les partial updates

## Endpoints API

| Methode | Path | Auth | Description |
|---------|------|------|-------------|
| POST | /api/v1/auth/login | Non | Login admin |
| GET | /api/v1/bootcamps | Non | Liste bootcamps publies |
| GET | /api/v1/bootcamps/{id} | Non | Detail bootcamp + sessions |
| POST | /api/v1/registrations | Non | Inscription visiteur |
| POST | /api/v1/contact | Non | Message de contact |
| GET | /api/v1/services | Non | Liste services publies |
| GET/POST/PUT/DELETE | /api/v1/admin/* | JWT | CRUD admin |

## DB & Migrations

PostgreSQL 15 avec Flyway. Migrations dans `src/main/resources/db/migration/`.

| Migration | Description |
|-----------|-------------|
| V1 | Schema initial (bootcamps, registrations, alumni, projects, services, etc.) |
| V2 | Audit columns sur services |
| V3 | Audit columns sur contact_messages |
| V4 | Table admin_users |
| V5 | Table bootcamp_sessions |
| V6 | Fix enum constraints sessions (uppercase) |
| V7 | Fix registration status (uppercase + CHECK) |

Prochaine migration : **V8**

## Entites principales

### Bootcamp
`title, description, duration, audience, prerequisites, price, benefits(JSON), category, tag, iconName, featured, published, displayOrder`
→ `@OneToMany sessions`

### BootcampSession
`sessionName, cohortNumber, year, startDate, endDate, registrationDeadline, maxParticipants(=20), currentParticipants(=0), isFull, status(enum), format(enum), location, priceOverride, earlyBirdPrice, earlyBirdDeadline, isFeatured, published`
→ `@ManyToOne bootcamp`

### Registration
`bootcamp(@ManyToOne), bootcampTitle, firstName, lastName, email, phone, company, position, message, status(PENDING/CONFIRMED/CANCELLED/COMPLETED)`

### ContactMessage
`firstName, lastName, email, phone, company, subject, message, status, notes`

## Config (application.yml)

Variables d'environnement cles :
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION`
- `CORS_ALLOWED_ORIGINS`
- `MINIO_ENDPOINT`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`
- `SLACK_WEBHOOK_URL`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `NOTIFICATION_EMAIL_TO`

## Build

```bash
make compile     # compile (JAVA_HOME force Temurin 17)
make run         # spring-boot:run profil dev
make test        # tests
make up          # docker-compose up (db + minio + adminer)
make up-all      # docker-compose up --build (tout)
```
