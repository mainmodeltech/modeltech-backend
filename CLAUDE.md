# Backend — Spring Boot API

## Stack
Spring Boot 3.2.5 | Java 17 (Temurin) | PostgreSQL 15 | Flyway | JWT | MapStruct | Lombok | MinIO

## Package racine
`com.modeltech.datamasteryhub`

## Architecture (arborescence)
```
src/main/java/.../
  common/persistence/
    BaseEntity.java
    SoftDeleteRepository.java
  config/          → AsyncConfig, JpaConfig, OpenApiConfig
  exception/       → GlobalExceptionHandler, ResourceNotFoundException, ErrorResponse
  security/        → SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
  modules/
    auth/          → AdminUser entity, JWT login
    training/      → Bootcamp, BootcampSession, Registration, PromoCode
    cms/           → Service (prestations B2B)
    communication/ → ContactMessage
    notification/  → SlackNotifier, EmailNotifier (async)
```

## Pattern par module (TOUJOURS suivre)
```
modules/<module>/
  controller/
    PublicXxxController.java      → @RequestMapping("/api/v1/<plural>")
    AdminXxxController.java       → @RequestMapping("/api/v1/admin/<plural>")
  dto/
    request/CreateXxxRequest.java → @Data @Builder @NoArgsConstructor @AllArgsConstructor
    request/UpdateXxxRequest.java → idem, tous champs optionnels (pas de @NotBlank)
    response/XxxResponse.java     → @Data @Builder @NoArgsConstructor @AllArgsConstructor
  entity/Xxx.java                 → extends BaseEntity, @Getter @Setter @NoArgsConstructor
  enums/XxxStatus.java
  mapper/XxxMapper.java           → @Mapper(componentModel = "spring")
  repository/XxxRepository.java   → extends SoftDeleteRepository<Xxx, UUID>
  service/XxxService.java         → interface
  service/impl/XxxServiceImpl.java → @Service @RequiredArgsConstructor @Slf4j @Transactional(readOnly = true)
```

---

## BaseEntity — champs exacts (NE PAS LIRE le fichier)
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class BaseEntity {
    @CreatedDate  @Column(nullable = false, updatable = false)  private LocalDateTime createdAt;
    @CreatedBy    @Column(updatable = false)                    private String createdBy;
    @LastModifiedDate                                           private LocalDateTime updatedAt;
    @LastModifiedBy                                             private String updatedBy;
    @Column(name = "is_deleted", nullable = false)              private boolean isDeleted = false;
                                                                private LocalDateTime deletedAt;
                                                                private String deletedBy;
}
```

## SoftDeleteRepository — signatures exactes (NE PAS LIRE le fichier)
```java
@NoRepositoryBean
public interface SoftDeleteRepository<T extends BaseEntity, ID> extends JpaRepository<T, ID> {
    List<T> findAllByIsDeletedFalse();
    Page<T> findAllByIsDeletedFalse(Pageable pageable);
    Optional<T> findByIdAndIsDeletedFalse(ID id);
}
```

## SecurityConfig — requestMatchers exacts (NE PAS LIRE le fichier)
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/contact-messages").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/registrations").permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .anyRequest().permitAll()  // pour CORS preflight
)
```
Pour ajouter un endpoint public POST → ajouter un `.requestMatchers(HttpMethod.POST, "/api/v1/xxx").permitAll()` AVANT `.anyRequest()`.

## Exceptions (NE PAS LIRE les fichiers)
```java
// Lancer dans les services :
throw new ResourceNotFoundException("Xxx", "id", id);
// → message: "Xxx introuvable avec id: '<uuid>'"

// Constructeur alternatif :
throw new ResourceNotFoundException("message custom");

// ErrorResponse (record) :
record ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path, Map<String,String> validationErrors)
```

---

## Enums exactes

```java
// modules/training/enums/
enum SessionStatus   { DRAFT, UPCOMING, OPEN, CLOSED, IN_PROGRESS, COMPLETED, CANCELLED }
enum SessionFormat   { PRESENTIEL, REMOTE, HYBRID }
enum RegistrationStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }

// modules/communication/entity/ContactMessageStatus.java  (dans le package entity, PAS enums)
enum ContactMessageStatus { unread, read, replied, archived }  // ATTENTION: lowercase
```

---

## Entites existantes (champs)

### Bootcamp
`id(UUID) | title | description(TEXT) | duration | audience(TEXT) | prerequisites(TEXT) | price | benefits(text[]) | category(50, default "data") | tag | iconName | featured(Boolean=false) | published(Boolean=true) | displayOrder(Integer=0) | nextSession(@Deprecated)`
Relations: `@OneToMany sessions (mappedBy="bootcamp", cascade=ALL, orphanRemoval=true, LAZY, @OrderBy startDate ASC)`

### BootcampSession
`id(UUID) | sessionName | cohortNumber(Integer) | year(Integer) | startDate(LocalDate) | endDate(LocalDate) | registrationDeadline(LocalDate) | maxParticipants(Integer=20) | currentParticipants(Integer=0) | isFull(Boolean=false) | status(SessionStatus=UPCOMING) | format(SessionFormat=PRESENTIEL) | location | priceOverride | earlyBirdPrice | earlyBirdDeadline(LocalDate) | isFeatured(Boolean=false) | published(Boolean=true)`
Relations: `@ManyToOne bootcamp (LAZY, nullable=false)`

### Registration
`id(UUID) | bootcamp(@ManyToOne LAZY) | bootcampTitle | session(@ManyToOne LAZY, bootcamp_session_id) | sessionName | promoCodeId(UUID) | promoCodeUsed(50) | discountPercent(Integer) | firstName | lastName | email | phone | company | position | message(TEXT) | status(RegistrationStatus=PENDING)`

### PromoCode
`id(UUID) | code(unique,50) | description | referrerName | referrerEmail | referrerPhone(50) | discountPercent(Integer=0) | maxUses(Integer, nullable) | usageCount(Integer=0) | expiresAt(LocalDateTime, nullable) | isActive(Boolean=true)`

### ContactMessage
`id(UUID) | firstName | lastName | email | phone | company | subject | message(TEXT) | status(ContactMessageStatus=unread) | notes(TEXT)`
**ATTENTION**: ContactMessage utilise `@Builder` (historique, devrait etre retire).

### Service (cms)
`id(UUID) | title | description | iconName | features(text[]) | duration | displayOrder(Integer=0) | published(boolean=true)`
**ATTENTION**: Service utilise `@Builder` (historique, devrait etre retire).

---

## Flyway migrations

| N | Description |
|---|-------------|
| V1 | Schema initial (bootcamps, registrations, alumni, projects, services, etc.) |
| V2 | Audit columns sur services |
| V3 | Audit columns sur contact_messages |
| V4 | Table admin_users |
| V5 | Table bootcamp_sessions |
| V6 | Fix enum constraints sessions (uppercase) |
| V7 | Fix registration status (uppercase + CHECK) |
| V8 | Add session and promo to registrations |
| V9 | Create promo_codes |

**Prochaine migration : V10**

---

## Endpoints API complets

| Methode | Path | Auth | Description |
|---------|------|------|-------------|
| POST | `/api/v1/auth/login` | Non | Login admin → JWT |
| GET | `/api/v1/bootcamps` | Non | Liste bootcamps publies |
| GET | `/api/v1/bootcamps/{id}` | Non | Detail bootcamp + sessions |
| GET | `/api/v1/services` | Non | Liste services publies |
| GET | `/api/v1/services/{id}` | Non | Detail service publie |
| POST | `/api/v1/registrations` | Non | Inscription visiteur |
| POST | `/api/v1/contact-messages` | Non | Message de contact |
| * | `/api/v1/admin/bootcamps/**` | JWT ADMIN | CRUD bootcamps |
| * | `/api/v1/admin/bootcamp-sessions/**` | JWT ADMIN | CRUD sessions |
| * | `/api/v1/admin/registrations/**` | JWT ADMIN | CRUD inscriptions |
| * | `/api/v1/admin/services/**` | JWT ADMIN | CRUD services |
| * | `/api/v1/admin/contact-messages/**` | JWT ADMIN | CRUD messages |
| * | `/api/v1/admin/promo-codes/**` | JWT ADMIN | CRUD codes promo |

---

## Config (application.yml) — variables d'env
```
DB_URL, DB_USER, DB_PASSWORD
JWT_SECRET, JWT_EXPIRATION (default 86400000)
CORS_ALLOWED_ORIGINS (default localhost:5173,3000,8080)
MINIO_ENDPOINT, MINIO_ROOT_USER, MINIO_ROOT_PASSWORD, MINIO_BUCKET (default "media")
SLACK_WEBHOOK_URL
MAIL_HOST (default smtp.gmail.com), MAIL_PORT (default 587), MAIL_USERNAME, MAIL_PASSWORD
MAIL_FROM (default noreply@model-technologie.com)
NOTIFICATION_EMAIL_TO (default business.modeltech@gmail.com)
SERVER_PORT (default 8080)
SPRING_PROFILES_ACTIVE (default dev)
```

---

## NE PAS FAIRE

1. **PAS de @Builder sur les entities** — Lombok @Builder ne gere pas les champs herites de BaseEntity, MapStruct echoue. Utiliser `@Getter @Setter @NoArgsConstructor`.
2. **PAS de @Builder.Default sur les entities** — meme raison.
3. **PAS de hard delete** — toujours soft delete via `entity.setDeleted(true); entity.setDeletedAt(LocalDateTime.now()); entity.setDeletedBy("system");`
4. **PAS de Java 25** — incompatible avec Lombok 1.18.30. Toujours utiliser Temurin 17 via Makefile.
5. **PAS de `isDeleted` dans @Mapping ignore** — MapStruct ne genere pas de setter pour le champ boolean `isDeleted` (prefixe `is`). Laisser commente (`// @Mapping(target = "isDeleted", ignore = true)`).
6. **PAS de `ddl-auto: update`** — Flyway gere le schema, JPA est en mode `validate`.
7. **PAS de FetchType.EAGER** sur les relations — toujours LAZY.
8. **PAS de @AllArgsConstructor sur les entities** — reserve aux DTOs.

---

## TEMPLATE COPIER-COLLER : nouveau module

Remplacer `Xxx` par le nom de l'entite, `xxx` par le chemin URL, `<module>` par le module.

### 1. Entity
```java
package com.modeltech.datamasteryhub.modules.<module>.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "xxx_table_name")
@Getter @Setter @NoArgsConstructor
public class Xxx extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    // ... champs metier
}
```

### 2. Repository
```java
package com.modeltech.datamasteryhub.modules.<module>.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.<module>.entity.Xxx;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface XxxRepository extends SoftDeleteRepository<Xxx, UUID> {
    List<Xxx> findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc();
    Optional<Xxx> findByIdAndPublishedTrueAndIsDeletedFalse(UUID id);
}
```

### 3. DTOs
```java
// --- CreateXxxRequest.java ---
package com.modeltech.datamasteryhub.modules.<module>.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Creer un xxx")
public class CreateXxxRequest {
    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255)
    private String title;
    // ... champs metier (PAS id, PAS audit fields)
}

// --- UpdateXxxRequest.java ---
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "MAJ un xxx (tous champs optionnels)")
public class UpdateXxxRequest {
    @Size(max = 255)
    private String title;
    // ... memes champs que Create, SANS @NotBlank
}

// --- XxxResponse.java ---
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class XxxResponse {
    private UUID id;
    private String title;
    // ... champs metier
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
```

### 4. Mapper (avec TOUS les @Mapping ignore BaseEntity)
```java
package com.modeltech.datamasteryhub.modules.<module>.mapper;

import com.modeltech.datamasteryhub.modules.<module>.dto.request.*;
import com.modeltech.datamasteryhub.modules.<module>.dto.response.*;
import com.modeltech.datamasteryhub.modules.<module>.entity.Xxx;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface XxxMapper {

    XxxResponse toResponse(Xxx entity);
    List<XxxResponse> toResponseList(List<Xxx> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    // @Mapping(target = "isDeleted", ignore = true)  // NE PAS DECOMMENTER — boolean prefix
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    Xxx toEntity(CreateXxxRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    // @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateEntity(UpdateXxxRequest request, @MappingTarget Xxx entity);
}
```

### 5. Service interface
```java
package com.modeltech.datamasteryhub.modules.<module>.service;

import com.modeltech.datamasteryhub.modules.<module>.dto.request.*;
import com.modeltech.datamasteryhub.modules.<module>.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface XxxService {
    XxxResponse create(CreateXxxRequest request);
    Page<XxxResponse> findAllForAdmin(Pageable pageable);
    XxxResponse findByIdForAdmin(UUID id);
    XxxResponse update(UUID id, UpdateXxxRequest request);
    void softDelete(UUID id);
    List<XxxResponse> findAllPublished();
    XxxResponse findPublishedById(UUID id);
}
```

### 6. Service impl
```java
package com.modeltech.datamasteryhub.modules.<module>.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.<module>.dto.request.*;
import com.modeltech.datamasteryhub.modules.<module>.dto.response.*;
import com.modeltech.datamasteryhub.modules.<module>.entity.Xxx;
import com.modeltech.datamasteryhub.modules.<module>.mapper.XxxMapper;
import com.modeltech.datamasteryhub.modules.<module>.repository.XxxRepository;
import com.modeltech.datamasteryhub.modules.<module>.service.XxxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class XxxServiceImpl implements XxxService {

    private final XxxRepository xxxRepository;
    private final XxxMapper xxxMapper;

    @Override @Transactional
    public XxxResponse create(CreateXxxRequest request) {
        log.info("Creation xxx: {}", request.getTitle());
        Xxx entity = xxxMapper.toEntity(request);
        return xxxMapper.toResponse(xxxRepository.save(entity));
    }

    @Override
    public Page<XxxResponse> findAllForAdmin(Pageable pageable) {
        return xxxRepository.findAllByIsDeletedFalse(pageable).map(xxxMapper::toResponse);
    }

    @Override
    public XxxResponse findByIdForAdmin(UUID id) {
        return xxxMapper.toResponse(xxxRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Xxx", "id", id)));
    }

    @Override @Transactional
    public XxxResponse update(UUID id, UpdateXxxRequest request) {
        Xxx entity = xxxRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Xxx", "id", id));
        xxxMapper.updateEntity(request, entity);
        return xxxMapper.toResponse(xxxRepository.save(entity));
    }

    @Override @Transactional
    public void softDelete(UUID id) {
        Xxx entity = xxxRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Xxx", "id", id));
        entity.setDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy("system");
        xxxRepository.save(entity);
    }

    @Override
    public List<XxxResponse> findAllPublished() {
        return xxxRepository.findAllByPublishedTrueAndIsDeletedFalseOrderByDisplayOrderAsc()
                .stream().map(xxxMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public XxxResponse findPublishedById(UUID id) {
        return xxxMapper.toResponse(xxxRepository.findByIdAndPublishedTrueAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Xxx", "id", id)));
    }
}
```

### 7. Controllers
```java
// --- PublicXxxController.java ---
package com.modeltech.datamasteryhub.modules.<module>.controller;

import com.modeltech.datamasteryhub.modules.<module>.dto.response.*;
import com.modeltech.datamasteryhub.modules.<module>.service.XxxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/xxx")
@RequiredArgsConstructor
@Tag(name = "Public - Xxx")
public class PublicXxxController {
    private final XxxService xxxService;

    @GetMapping
    @Operation(summary = "Lister les xxx publies")
    public ResponseEntity<List<XxxResponse>> getAll() {
        return ResponseEntity.ok(xxxService.findAllPublished());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail xxx publie")
    public ResponseEntity<XxxResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(xxxService.findPublishedById(id));
    }
}

// --- AdminXxxController.java ---
package com.modeltech.datamasteryhub.modules.<module>.controller;

import com.modeltech.datamasteryhub.modules.<module>.dto.request.*;
import com.modeltech.datamasteryhub.modules.<module>.dto.response.*;
import com.modeltech.datamasteryhub.modules.<module>.service.XxxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/xxx")
@RequiredArgsConstructor
@Tag(name = "Admin - Xxx")
public class AdminXxxController {
    private final XxxService xxxService;

    @PostMapping
    @Operation(summary = "Creer un xxx")
    public ResponseEntity<XxxResponse> create(@Valid @RequestBody CreateXxxRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(xxxService.create(request));
    }

    @GetMapping
    @Operation(summary = "Lister tous les xxx (admin, pagine)")
    public ResponseEntity<Page<XxxResponse>> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(xxxService.findAllForAdmin(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail xxx (admin)")
    public ResponseEntity<XxxResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(xxxService.findByIdForAdmin(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "MAJ xxx")
    public ResponseEntity<XxxResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateXxxRequest request) {
        return ResponseEntity.ok(xxxService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer xxx (soft)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        xxxService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 8. Migration Flyway
```sql
-- V10__Create_xxx_table.sql
CREATE TABLE xxx_table_name (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    -- ... champs metier ...
    published BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER DEFAULT 0,
    -- BaseEntity audit columns
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255)
);
```

---

## Notifications async (pattern existant)
```java
// Dans le service, injecter :
private final NotificationService notificationService;

// Apres save, appeler (async, fire-and-forget) :
notificationService.notifyNewRegistration(saved);
```
`NotificationService` envoie Slack webhook + email via `@Async`.

## Build
```bash
make compile     # compile (JAVA_HOME force Temurin 17)
make run         # spring-boot:run profil dev
make test        # tests
make up          # docker-compose up (db + minio + adminer)
make up-all      # docker-compose up --build (tout)
```
