# Backend — Spring Boot API

## Stack
Spring Boot 3.2.5 | Java 17 (Temurin) | PostgreSQL 15 | Flyway | JWT | MapStruct | Lombok | MinIO

## Package racine
`com.modeltech.datamasteryhub`

## Architecture (arborescence)
```
src/main/java/.../
  common/
    persistence/
      BaseEntity.java
      SoftDeleteRepository.java
    dto/
      ApiResponse.java          ← enveloppe standard TOUTES les réponses API
  config/          → AsyncConfig, JpaConfig, OpenApiConfig
  exception/       → GlobalExceptionHandler, ResourceNotFoundException, ErrorResponse
  security/        → SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
  modules/
    auth/          → AdminUser entity, JWT login
    training/      → Bootcamp, BootcampSession, Registration, PromoCode
    cms/           → Service (prestations B2B)
    communication/ → ContactMessage, Masterclass
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

## SecurityConfig — principe (les listes de chemins évoluent : lire le fichier avant de la modifier)
- Liste blanche **explicite** : `OPTIONS /**`, `GET /actuator/health`, quelques `POST` publics (auth login/forgot/reset, registrations, contact-messages, masterclass/register) et des `GET` publics (bootcamps, formations, domains, partners, services, témoignages publiés, alumni, projets, sessions, promo-codes/validate), Swagger.
- **`.anyRequest().authenticated()`** : tout le reste exige un JWT. Un nouveau `GET` public doit être ajouté à la liste (avec son `/**` si le détail `/{id}` est public aussi — oubli déjà vu sur `/bootcamps/{id}`).
- **Contrôle de rôle sur `/api/v1/admin/**`** (lot learner-accounts, ordre des règles = ordre dans le fichier) : `/admin/users/**` → `SUPER_ADMIN` ; `/admin/formations/*/content` → + `PARTNER` (propriété vérifiée par le service) ; `/learner/**` → `LEARNER` ; `/admin/sessions/**` → `SUPER_ADMIN`/`ADMIN`/`TRAINER` ; `/admin/lessons/*/quiz` et `/admin/formations/*/project` comme le programme ; `/admin/registrations|promo-codes|learners|payments|enrollments/**` → `SUPER_ADMIN`/`ADMIN` ; le reste de `/admin/**` → `SUPER_ADMIN`/`ADMIN`/`EDITOR`. Un compte apprenant (`ROLE_LEARNER`) n'entre jamais dans `/admin/**`. Nouvelle zone admin sensible : ajouter sa règle **avant** `/admin/**`.
- Le filtre JWT recharge le compte (admin ou apprenant) à chaque requête : rôles et statut `active` viennent toujours de la base, pas du jeton (un compte désactivé est refusé immédiatement). Le jeton porte aussi `roles` et `uty` (`ADMIN`/`LEARNER`) pour le front.
- **Formulaires publics** : toujours via `IpRateLimiter.check(request, "<scope>")` (429 au-delà de `app.rate-limit.forms.per-hour`, 10 par défaut). L'IP est la **dernière** entrée de `X-Forwarded-For` (celle du reverse proxy), jamais la première (falsifiable).
- **Connexion** : `POST /auth/login` est limité par IP (`app.rate-limit.login.per-hour`, 30 par heure) ; formulaires publics : `app.rate-limit.forms.per-hour`.
- **reCAPTCHA** : `recaptchaService.verify()` retourne un booléen qu'il faut **toujours tester** (inscription et masterclass → 403). Désactivé par le profil `dev` seulement.
- **Erreurs 500** : le client ne reçoit jamais le message technique, seulement « Référence : xxxxxxxx » ; la trace complète est dans les logs avec la même référence.
- Mise en production : voir `docs/deploiement.md` (ordre de fusion, variables d'environnement, contrôles SQL, retour arrière).
- Les erreurs métier se lèvent avec `ResponseStatusException` (gérée par `GlobalExceptionHandler` → statut conservé) ou `ResourceNotFoundException` (404).

---

## Enveloppe de réponse API — ApiResponse<T>

**TOUTES les réponses contrôleurs doivent utiliser `ApiResponse<T>`** — jamais de `ResponseEntity<Page<XxxResponse>>` brut.

```java
// package : com.modeltech.datamasteryhub.common.dto

// Réponse simple
ApiResponse.ok("Message", data)

// Réponse paginée (extrait automatiquement le content du Page Spring)
ApiResponse.page("N élément(s)", pageResult)

// Erreur
ApiResponse.error("Message d'erreur")
```

Structure JSON résultante :
```json
{
  "success": true,
  "message": "...",
  "data": [...],
  "pagination": {          // présent uniquement pour les listes paginées
    "page": 0,
    "size": 20,
    "totalElements": 47,
    "totalPages": 3
  }
}
```

**`PaginationMeta`** est une inner class de `ApiResponse` — pas besoin de l'importer séparément.

### Exemple contrôleur admin paginé
```java
@GetMapping
public ResponseEntity<ApiResponse<List<XxxResponse>>> getAll(
        @PageableDefault(size = 20) Pageable pageable) {
    Page<XxxResponse> page = xxxService.findAllForAdmin(pageable);
    return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " élément(s)", page));
}
```

### Exemple contrôleur avec réponse simple
```java
@PostMapping
public ResponseEntity<ApiResponse<XxxResponse>> create(@Valid @RequestBody CreateXxxRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Xxx créé avec succès", xxxService.create(req)));
}
```

---

## Exceptions (NE PAS LIRE les fichiers)

### Lancer dans les services
```java
// Ressource introuvable (404)
throw new ResourceNotFoundException("Xxx", "id", id);
// → message: "Xxx introuvable avec id: '<uuid>'"

// Constructeur message libre
throw new ResourceNotFoundException("Message custom");

// Session complète, doublon, etc. (4xx Spring natif)
throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette session est complète.");
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le profil est obligatoire.");
```

### GlobalExceptionHandler — handlers présents
| Exception                        | HTTP | Message                                     |
|----------------------------------|------|---------------------------------------------|
| `MethodArgumentNotValidException`| 400  | Validation Failed + map champ→message       |
| `ResourceNotFoundException`      | 404  | Message de l'exception                      |
| `BadCredentialsException`        | 401  | "Email ou mot de passe incorrect"           |
| `DisabledException`              | 401  | "Compte désactivé, contactez un admin"      |
| `Exception` (catch-all)          | 500  | « Une erreur est survenue. Référence : … »  |

### ErrorResponse — record exact
```java
record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors  // null sauf pour 400 validation
)
```

---

## Enums exactes

```java
// modules/training/enums/
enum SessionStatus       { DRAFT, UPCOMING, OPEN, CLOSED, IN_PROGRESS, COMPLETED, CANCELLED }
enum SessionFormat       { PRESENTIEL, REMOTE, HYBRID }
enum RegistrationStatus  { PENDING, PAYMENT_PENDING, PAYMENT_TO_CONFIRM, CONFIRMED, CANCELLED, COMPLETED, REJECTED }
enum DeliveredBy         { INTERNAL, PARTNER }
enum PaymentStatus       { PENDING, DECLARED, CONFIRMED, CANCELLED }
enum PaymentMethod       { WAVE, ORANGE_MONEY, VIREMENT, ENTREPRISE, ESPECES }   // seuls les 3 premiers sont déclarables par le candidat
enum EnrollmentStatus    { ACTIVE, SUSPENDED, COMPLETED, CANCELLED }
enum PayerType           { INDIVIDUAL, COMPANY }
enum FormationLevel      { DEBUTANT, INTERMEDIAIRE, AVANCE }
enum FormationFormat     { PRESENTIEL, EN_LIGNE, HYBRIDE }   // ≠ SessionFormat (REMOTE/HYBRID) : voulu, c'est le contrat du front

// modules/training/entity/RegistrationProfile.java  (dans le package entity, PAS enums)
enum RegistrationProfile { STUDENT, PROFESSIONAL, ENTREPRENEUR }

// modules/communication/entity/ContactMessageStatus.java  (dans le package entity, PAS enums)
enum ContactMessageStatus { unread, read, replied, archived }  // ATTENTION: lowercase
```

---

## Entités existantes (champs)

### Bootcamp (= « Formation » côté front : même id, même table)
`id(UUID) | title | description(TEXT) | duration | audience(TEXT) | prerequisites(TEXT) | price(String d'affichage) | benefits(text[]) | category(50, default "data") | tag | iconName | featured(Boolean=false) | published(Boolean=true) | displayOrder(Integer=0) | nextSession(@Deprecated)`
Contenu riche (V17, nullables) : `tagline | colorKey | profiles, tools, curriculum, outcomes (jsonb, listes) | certification (jsonb)`
Catalogue (V18) : `slug(unique, NOT NULL, généré depuis le titre — jamais modifié si le titre change) | domain(@ManyToOne LAZY, nullable) | deliveredBy(DeliveredBy=INTERNAL) | partner(@ManyToOne LAZY) | level | format | certificationPrep(TEXT) | targetRoles(text[]) | relatedFormations(@ManyToMany self, table bootcamp_related)`
Règles : `PARTNER` ⇒ partenaire obligatoire (CHECK SQL + 400) ; `INTERNAL` efface le partenaire. Une formation **sans domaine** n'apparaît pas dans `/formations` (le front exige `domain`) mais reste dans `/bootcamps`.
Relations: `@OneToMany sessions (mappedBy="bootcamp", cascade=ALL, orphanRemoval=true, LAZY, @OrderBy startDate ASC)`

### Domain (V18)
`id | slug(unique) | name | description | badge | comingSoon(Boolean=false) | visible(Boolean=true) | displayOrder(Integer=0)` — le domaine « Data & BI » (`data-bi`) est créé par la migration et rattaché aux formations existantes.

### Partner (V18)
`id | slug(unique) | name | logoUrl | logoObjectKey | bio | website | contactName | contactEmail | contactPhone | revenueSharePercent(0-100) | active(Boolean=true)`
⚠️ `revenueSharePercent` et les champs `contact*` sont **internes** : jamais dans `PartnerResponse` (API publique) ; seulement dans `AdminPartnerResponse`.

### BootcampSession
`id(UUID) | sessionName | cohortNumber(Integer) | year(Integer) | startDate(LocalDate) | endDate(LocalDate) | registrationDeadline(LocalDate) | maxParticipants(Integer=20) | currentParticipants(Integer=0) | isFull(Boolean=false) | status(SessionStatus=UPCOMING) | format(SessionFormat=PRESENTIEL) | location | priceOverride | earlyBirdPrice | earlyBirdDeadline(LocalDate) | isFeatured(Boolean=false) | published(Boolean=true) | schedule (V17)`
Relations: `@ManyToOne bootcamp (LAZY, nullable=false)`

### Registration
`id(UUID) | bootcamp(@ManyToOne LAZY) | bootcampTitle | session(@ManyToOne LAZY, bootcamp_session_id) | sessionName | promoCodeId(UUID) | promoCodeUsed(50) | discountPercent(Integer) | firstName | lastName | email | phone | country | profile(RegistrantProfile) | school | company | position | message(TEXT) | status(RegistrationStatus=PENDING)`

**Champs ajoutés (V10) :** `country`, `profile`, `school`
**Champs ajoutés (V21) :** `acceptedAt/acceptedBy | rejectedReason | payerType | totalAmount (XOF, figé à l'acceptation) | learner(@ManyToOne LAZY)`

### PromoCode
`id(UUID) | code(unique,50) | description | referrerName | referrerEmail | referrerPhone(50) | discountPercent(Integer=0) | maxUses(Integer, nullable) | usageCount(Integer=0) | expiresAt(LocalDateTime, nullable) | isActive(Boolean=true)`

### ContactMessage
`id(UUID) | firstName | lastName | email | phone | company | subject | message(TEXT) | status(ContactMessageStatus=unread) | notes(TEXT) | type(ContactType, NOT NULL) | requesterType(PARTICULIER/ENTREPRISE, nullable) | details(jsonb, nullable)`
`type` : `CONTACT` (formulaire de contact), `DIAGNOSTIC` (page Entreprises), `PARTNER_APPLICATION` (page Partenaires) — les trois sont stockés dans la même table et listés par `/admin/contact-messages` (filtre `?type=`). `lastName` est NOT NULL en base mais **facultatif** à l'entrée : un nom d'un seul mot est stocké avec `lastName = ""`. Les enums de formulaire (`PeopleCount`, `TrainingNeed`, `PartnerDomain`) sont dans `modules/communication/enums/` avec leur libellé français (`getLabel()`).
**ATTENTION**: ContactMessage utilise `@Builder` (historique, devrait être retiré).

### Paiements et accès (V21)
`Payment` = une **échéance** (1..N par inscription) : `registration | amount(Long, XOF) | currency | installmentNumber/Count | dueDate | status | method | reference | declaredAt | confirmedAt/By | rejectionReason | proofObjectKey/proofUrl (MinIO, dossier payment-proofs) | publicToken (unique, 256 bits) + tokenExpiresAt | reminderCount/lastReminderAt | invoiceRef | purchaseOrderRef | notes`. `Enrollment` = accès apprenant (`learner | session (nullable) | registration (unique) | status | accessStartsAt/EndsAt` = dates de la session), créé à la **première échéance confirmée**. `Bootcamp.priceAmount/currency`, `BootcampSession.priceOverrideAmount/earlyBirdAmount` : montants numériques, **nuls tant que l'équipe ne les saisit pas** (les prix texte d'affichage restent, jamais convertis).
Règle de prix (`RegistrationPricing`) : prix session (sinon formation) → remplacé par l'early-bird si la candidature date au plus tard de `earlyBirdDeadline` → puis `discountPercent` du code promo. L'admin peut imposer `totalAmount` à l'acceptation.
Config : `app.payment.default-due-days` (2), `link-validity-days` (30, après l'échéance), `reminder-days` (2), `max-reminders` (3), `reminder-cron` (9h) ; `app.frontend.payment-path` (`/paiement`, lien = `{app.frontend.url}/paiement/{token}`).

### Comptes (V13 + V20)
`AdminUser` (back-office : rôles `SUPER_ADMIN`, `ADMIN`, `EDITOR`, `TRAINER`, `PARTNER` ; `partner` LAZY obligatoire pour `PARTNER`) et `Learner` (apprenant, rôle `LEARNER`, `passwordHash` **nul** tant que le lien d'invitation n'a pas été utilisé) sont deux tables distinctes ; une même adresse e-mail ne peut exister que dans l'une des deux (409). Constantes de rôles : `RoleNames`. Login/me/reset sont communs (`AuthService` cherche l'admin puis l'apprenant). Invitation : `authService.createPasswordResetToken(email, minutes)` + `passwordSetupLink(token, learner)` + `notificationService.sendAccountInvitationEmail(...)` (validité 72 h). Pour créer un compte à la confirmation du paiement : `LearnerService.findOrCreateInvited(...)`.

### NewsletterSubscription (V19)
`id | email(unique, minuscules) | status(PENDING/CONFIRMED/UNSUBSCRIBED) | source | confirmationToken | confirmationExpiresAt | unsubscribeToken | confirmedAt | unsubscribedAt` — double opt-in ; **les jetons ne sortent jamais** dans l'API admin.

### Programme des formations (V23, module `course`)
`CourseConfig` (1 par formation : `sequentialUnlock | accessDuration (12_MONTHS|LIFETIME) | règles du certificat | contentUpdatedAt`, créé à la 1re sauvegarde ; valeurs par défaut `app.course.default-*`) → `CourseModule` → `CourseLesson` (`type VIDEO|RESOURCE|LIVE|QUIZ`, `status PUBLISHED|SCHEDULED|DRAFT`, réglages de quiz, `liveAt` = heure locale du site, fuseau `app.timezone` = Africa/Dakar) → `LessonResource`. `LessonProgress` = (apprenant, leçon, completed, positionSeconds).
Édition = **remplacement de l'arbre complet** (`PUT /admin/formations/{id}/content`) : les identifiants inconnus (temporaires de l'éditeur) créent, les connus mettent à jour, les absents sont **soft-deleted** ; l'ordre du tableau fait foi ; tout est validé avant la moindre écriture. Liens vidéo/live/ressources : `http(s)://` uniquement (jamais `javascript:`).
Vue apprenant (`CourseContentAssembler.assemble(b, true)`) : brouillons masqués ; vidéo des leçons non publiées et lien des ressources `lockedUntilQuiz` **jamais livrés**. Le serveur impose : accès (inscription ACTIVE/COMPLETED, ouverte à la date de début, 12 mois après la fin sauf LIFETIME), déblocage séquentiel des modules, un quiz ne se valide qu'en le réussissant.
Les endpoints `/learner/**` et `/admin/formations/*/content` renvoient du **JSON brut** (contrat front `course.type.ts`), pas d'enveloppe `ApiResponse`.

### Évaluations (V24, module `course`)
`QuizQuestion`/`QuizChoice` = banque d'une leçon QUIZ (exactement 1 bonne réponse, 2 à 8 choix, 200 questions max). `QuizAttempt` = questions tirées (`questionIds`, taille = `quiz.questionCount`) + réponses figées + score/réussite : **le serveur tire, corrige et compte les tentatives** ; les bonnes réponses ne sortent jamais avant la soumission ; la correction (`review`) n'est livrée qu'après une réussite ou à la dernière tentative (sinon on pourrait « jouer » la banque) ; une tentative ouverte est reprise, pas recréée ; réussir un quiz marque la leçon terminée (seul moyen de la terminer). Pas de chrono (`timeLimitMinutes` toujours null).
`CourseProject` (1 par formation : consigne, échéance texte, extensions, taille max), `ProjectSubmission` (1 par apprenant et formation, statut NOT_STARTED→SUBMITTED→CHANGES_REQUESTED/VALIDATED, verrouillé une fois VALIDATED), `ProjectFile` (clé MinIO aléatoire, **jamais d'URL publique** : lien signé de 15 min côté back-office), `ProjectFeedback`. `LiveRollCall`/`LiveAttendance` : l'appel d'un live (présence « inconnue » tant qu'il n'est pas fait ; seuls les lives appelés comptent dans la présence).
Conditions du certificat (`EvaluationServiceImpl`) : leçons ≥ `lessonsCompletedPercent`, **tous** les quiz réussis, présence aux lives ≥ `livePresencePercent`, projet VALIDATED si `finalProjectValidated`. Statut de suivi : READY si tout est rempli, AT_RISK si non et la session finit dans 7 jours ou moins, sinon PENDING (pas d'ISSUED : aucune délivrance de certificat n'existe).
`StorageService.uploadDocument(file, folder, extensions, maxBytes)` : documents hors images, stockés en `application/octet-stream`. `StorageException` → 400 (handler ajouté ; auparavant 500).
Services partagés : `LearnerAccess` (accès apprenant, déblocage séquentiel), `CourseAccessPolicy` (qui édite le contenu : personnel + partenaire propriétaire).

### Messagerie multi-canaux (V28)
- **Canaux** (`modules/notification/channel`) : `NotificationChannel` (`name`, `canReach`, `send`), `EmailChannel`, `MessageDispatcher` (envoie sur les canaux de `app.messaging.channels`, `EMAIL` par défaut ; un canal en panne ne bloque pas les autres). **Ajouter WhatsApp** = un `@Component implements NotificationChannel` (qui lit `OutboundMessage.toPhone`) + `app.messaging.channels=EMAIL,WHATSAPP`. Aucun appelant à modifier.
- **Rappels de live automatiques** (`LiveReminderScheduler`, toutes les 15 min) : 24 h puis 1 h avant chaque live (non brouillon), une seule fois par (live, session, moment) grâce à `live_reminders` ; seuls les apprenants dont l'accès est ouvert.
- **Messages de l'équipe** : `POST /admin/sessions/{id}/messages` (toute la session ou `learnerIds`, 300 max, envoi en tâche de fond, historique `GET …/messages`) ; l'adresse de réponse est celle de l'expéditeur. Réservé à l'administration et au formateur de la session.
- **Questions** : l'apprenant pose une question depuis une leçon (`POST /learner/lessons/{id}/questions`, 5 en attente max par leçon) → e-mail au formateur de la session (sinon `NOTIFICATION_EMAIL_TO`) ; l'équipe répond (`POST /admin/sessions/{sid}/questions/{qid}/answer`) → e-mail à l'apprenant ; les questions d'un apprenant restent privées.

### Formateurs, factures, annulations (V27)
- **Formateur de session** : `BootcampSession.trainer` (compte `AdminUser` actif avec `ROLE_TRAINER`), affecté par `PUT /admin/bootcamps/sessions/{id}/trainer` (ADMIN) ; liste : `GET /admin/trainers`. `SessionAccessPolicy` : l'administration voit toutes les sessions, **un formateur uniquement les siennes** (suivi, appel, rendus de projet, délivrance de certificats). Son nom alimente le suivi de session, le cours apprenant et le certificat.
- **Inscription manuelle** : `POST /admin/registrations` (`source = ADMIN`, sans reCAPTCHA ni e-mail automatique), puis acceptation normale.
- **Annulation** `POST /admin/registrations/{id}/cancel {reason}` : échéances ouvertes annulées, place libérée et accès fermé si elle était confirmée ; **remboursement** `POST /admin/payments/{id}/refund {reason}` : consigne un remboursement fait hors plateforme (statut `REFUNDED`) ; plus rien de payé ⇒ inscription annulée.
- **Factures** (`Invoice`, document figé à l'émission) : numéro `FAC-{année}-{n°:05d}` **continu** (compteur `invoice_counters` verrouillé, pas de séquence à trous), une seule facture `ISSUED` par inscription (une annulée se refait), total = montant à payer de l'inscription (TVA **incluse** : `app.invoice.vat-percent`, 0 par défaut), détail early-bird / remise promo reconstitué quand le montant n'a pas été négocié. PDF : `InvoicePdfGenerator`. Mentions légales par `app.invoice.seller.*` (nom, adresse, téléphone, e-mail, `tax-id` NINEA, `register-number` RCCM, `payment-details`, `footer`) : **rien n'est inventé**, ce qui est vide n'est pas imprimé.

### Certificats (V26, module `course`)
`Certificate` : numéro public `MT-{année}-{CODE}-{n°:05d}-{4 car. aléatoires}` (le suffixe aléatoire empêche d'énumérer les certificats via la page publique), **photographie figée à la délivrance** (nom, formation, durée, compétences = `benefits`, signataire, `includesProject`) ; statuts `VALID`/`REVOKED` ; un seul VALID par (apprenant, formation) (index unique partiel). `CODE` = `Bootcamp.certificateCode`, sinon un mot en majuscules du titre (VBA), sinon les initiales.
- **Délivrance automatique** (`CertificateService.issueIfEligible`) déclenchée par : leçon terminée, quiz réussi, projet validé, appel d'un live ; + balayage nocturne (`app.certificate.sweep-cron`). Conditions = `CertificateConditions` (source unique, partagée avec l'écran d'évaluations et le suivi de session) ; une formation **sans programme** ne délivre jamais. **Jamais de redélivrance automatique** après une révocation.
- **Manuelle** : `POST /admin/sessions/{sid}/learners/{lid}/certificate` ; non éligible ⇒ 409 avec les conditions manquantes ; `force` = dérogation réservée ADMIN/SUPER_ADMIN, motif obligatoire, tracée (`forced`, `forceReason`).
- **PDF** : généré à la demande (`CertificatePdfGenerator`, OpenPDF + QR ZXing, A4 paysage, logo `src/main/resources/certificates/logo.png`), jamais stocké. Aperçu : `docs/design/certificat-apercu.png`.
- **E-mail** « certificat prêt » (PDF en pièce jointe + lien de vérification + lien LinkedIn) envoyé **après commit** (`CertificateIssuedListener`).
- Config : `app.certificate.signatory-name` / `signatory-title` (Patrick Lionnel DOOKO, Gérant), `linkedin-organization-id` (103600105), `id-prefix` (MT), `issuer`, `app.frontend.certificate-path` (`/certificats`).

### SiteSetting (V22, cms)
`id | key (unique, ^[a-z0-9][a-z0-9._-]*$, ≤100) | value (jsonb libre, ≤20 Ko)` — contenus **publics** du site (tarifs du coaching, coach, prochain atelier, étude de cas, accroches…). Aucun secret ici. Clé absente = le site masque le bloc. Aucune donnée n'est semée. Supprimer = soft delete ; la clé peut être recréée.

### Service (cms)
`id(UUID) | title | description | iconName | features(text[]) | duration | displayOrder(Integer=0) | published(boolean=true)`
**ATTENTION**: Service utilise `@Builder` (historique, devrait être retiré).

---

## Flyway migrations

| N   | Description                                            |
|-----|--------------------------------------------------------|
| V1  | Schema initial (bootcamps, registrations, alumni…)     |
| V2  | Audit columns sur services                             |
| V3  | Audit columns sur contact_messages                     |
| V4  | Table admin_users                                      |
| V5  | Table bootcamp_sessions                                |
| V6  | Fix enum constraints sessions (uppercase)              |
| V7  | Fix registration status (uppercase + CHECK)            |
| V8  | Add session and promo to registrations                 |
| V9  | Create promo_codes                                     |
| V10 | Add bootcamp/result to testimonials                    |
| V11 | Audit columns sur testimonials                         |
| V12 | Create masterclass_registrations                       |
| V13 | RBAC : roles, admin_user_roles, reset tokens, blacklist|
| V14 | Add country, profile (CHECK), school to registrations  |
| V15 | Module networking (alumni, projects, membres, captures)|
| V16 | Audit columns alumni/projects                          |
| V17 | Contenu riche bootcamp (jsonb), schedule, FK témoignage|
| V18 | domains, partners, bootcamp_related, champs formation + rétro-remplissage (slug, domaine data-bi) |
| V19 | contact_messages : type / requester_type / details (jsonb) ; table newsletter_subscriptions |
| V28 | rappels de live, messages de session, questions des apprenants |
| V27 | formateur de session, origine/annulation des inscriptions, remboursement d'échéance, factures (invoices + compteur continu) |
| V26 | certificates + séquence de numérotation + bootcamps.certificate_code |
| V25 | email_logs : journal des e-mails sortants (métadonnées seulement) |
| V24 | évaluations : banque de questions, tentatives de quiz, projet final (consigne, rendus, retours), appel des lives |
| V23 | programme des formations (course_configs, course_modules, course_lessons, lesson_resources) + lesson_progress |
| V22 | site_settings : contenus du site éditables (clé → JSON) |
| V21 | montants numériques (bootcamps, sessions), statuts PAYMENT_PENDING/PAYMENT_TO_CONFIRM/REJECTED, registrations (+acceptation, total, learner), tables payments et enrollments |
| V20 | learners + learner_roles, rôles LEARNER/TRAINER/PARTNER, admin_users.partner_id, rattrapage des rôles admin (+ amorçage d'un SUPER_ADMIN si aucun) |

**Prochaine migration : V29** — additive uniquement (jamais modifier une migration appliquée) ; tester le rétro-remplissage sur données existantes (cf. `V18BackfillMigrationIT`, `V20BackfillMigrationIT`).

> Numérotation indicative : la source de vérité est le dossier `src/main/resources/db/migration/`.

### V10 — référence
```sql
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS country TEXT;
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS profile TEXT
    CONSTRAINT registrations_profile_check
    CHECK (profile IN ('STUDENT', 'PROFESSIONAL', 'ENTREPRENEUR'));
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS school TEXT;
```

---

## Endpoints API complets

| Méthode | Path                                              | Auth       | Description                          |
|---------|---------------------------------------------------|------------|--------------------------------------|
| POST    | `/api/v1/auth/login`                             | Non        | Login admin → JWT                    |
| GET     | `/api/v1/bootcamps`                              | Non        | Liste bootcamps publiés              |
| GET     | `/api/v1/bootcamps/{id}`                         | Non        | Détail bootcamp + sessions           |
| GET     | `/api/v1/formations`                             | Non        | Catalogue (filtres : domain, level, format, deliveredBy, targetRole) — JSON brut |
| GET     | `/api/v1/formations/slug/{slug}`                 | Non        | Fiche formation par slug             |
| GET     | `/api/v1/formations/sessions`                    | Non        | Sessions OPEN/UPCOMING à plat (calendrier) |
| GET     | `/api/v1/domains`                                | Non        | Domaines visibles                    |
| GET     | `/api/v1/partners`                               | Non        | Partenaires (sans part de revenu)    |
| GET     | `/api/v1/services`                               | Non        | Liste services publiés               |
| GET     | `/api/v1/services/{id}`                          | Non        | Détail service publié                |
| POST    | `/api/v1/registrations`                          | Non        | Inscription visiteur bootcamp        |
| POST    | `/api/v1/contact-messages`                       | Non        | Message de contact (limité par IP)   |
| POST    | `/api/v1/diagnostic-requests`                    | Non        | Diagnostic gratuit entreprise (limité par IP) |
| POST    | `/api/v1/partner-applications`                   | Non        | Candidature formateur partenaire (limité par IP) |
| POST    | `/api/v1/newsletter/subscriptions` (+ `/confirm`, `/unsubscribe`) | Non | Newsletter, double opt-in (limité par IP) |
| GET     | `/api/v1/admin/masterclass/{id}/registrations`   | JWT ADMIN  | Inscriptions masterclass (paginé)    |
| GET     | `/api/v1/admin/masterclass/{id}/count`           | JWT ADMIN  | Nombre d'inscrits masterclass        |
| *       | `/api/v1/admin/bootcamps/**`                     | JWT ADMIN  | CRUD bootcamps                       |
| *       | `/api/v1/admin/domains/**`                       | JWT ADMIN  | CRUD domaines (ApiResponse)          |
| *       | `/api/v1/admin/partners/**` (+ `POST /{id}/logo`) | JWT ADMIN  | CRUD partenaires (ApiResponse)       |
| *       | `/api/v1/admin/bootcamp-sessions/**`             | JWT ADMIN  | CRUD sessions                        |
| *       | `/api/v1/admin/registrations/**`                 | JWT ADMIN  | CRUD inscriptions (paginé)           |
| *       | `/api/v1/admin/services/**`                      | JWT ADMIN  | CRUD services                        |
| *       | `/api/v1/admin/contact-messages/**`              | JWT ADMIN  | CRUD messages (`?type=` filtre)      |
| GET     | `/api/v1/admin/newsletter-subscriptions`         | JWT ADMIN  | Abonnés newsletter (`?status=`)      |
| *       | `/api/v1/admin/promo-codes/**`                   | JWT ADMIN  | CRUD codes promo                     |
| GET | `/api/v1/payments/{token}` | Jeton | Page « lien de paiement » (ApiResponse, sans donnée personnelle) |
| POST | `/api/v1/payments/{token}/declaration`, `/proof` (multipart `file`, image ≤ 5 Mo) | Jeton | Déclaration du paiement / capture (limités par IP) |
| POST | `/api/v1/admin/registrations/{id}/accept`, `/reject`, `/payments` | JWT ADMIN | Acceptation (calcul + échéances + lien), refus, paiement saisi à la main (ApiResponse) |
| GET/POST | `/api/v1/admin/payments` (`?status=&registrationId=`), `/{id}/confirm`, `/{id}/reject`, `/{id}/remind`, `/api/v1/admin/enrollments` | JWT ADMIN | File « paiement à confirmer », confirmation, refus, relance, accès |
| GET | `/api/v1/certificates/{publicId}` et `/pdf` | Non | Vérification publique et PDF (limités par IP ; PDF 410 si révoqué) |
| GET | `/api/v1/learner/certificates` | JWT LEARNER | Mes certificats (+ lien de vérification, PDF, LinkedIn) |
| POST/GET | `/api/v1/admin/sessions/{id}/messages`, `GET …/questions?open=`, `POST …/questions/{qid}/answer` | JWT ADMIN / TRAINER de la session | Messages aux apprenants, questions (ApiResponse) |
| GET/POST | `/api/v1/learner/lessons/{id}/questions` | JWT LEARNER | Poser / relire ses questions (JSON brut) |
| POST | `/api/v1/admin/registrations` (manuelle), `/{id}/cancel`, `/{id}/invoice` ; `POST /admin/payments/{id}/refund` | JWT ADMIN | Inscription manuelle, annulation, facture, remboursement (ApiResponse) |
| GET/POST | `/api/v1/admin/invoices` (`/{number}/pdf`, `/{number}/cancel`, `/{number}/send`), `GET /admin/registrations/{id}/invoices` | JWT ADMIN | Factures PDF des entreprises |
| GET | `/api/v1/payments/{token}/invoice` | Jeton | Facture téléchargeable depuis le lien de paiement |
| PUT/GET | `/api/v1/admin/bootcamps/sessions/{id}/trainer`, `/api/v1/admin/trainers` | JWT ADMIN / EDITOR | Affecter un formateur ; liste des formateurs |
| GET/POST | `/api/v1/admin/certificates` (`/{publicId}/revoke`, `/{publicId}/resend`), `POST /admin/sessions/{sid}/learners/{lid}/certificate` | JWT ADMIN (délivrer : + TRAINER) | Certificats (ApiResponse) |
| GET/PUT | `/api/v1/admin/formations/{id}/content` | JWT EDITOR+ / PARTNER (ses formations) | Programme de la formation (JSON brut) |
| GET/PUT | `/api/v1/admin/lessons/{id}/quiz` ; GET/PUT/DELETE `/admin/formations/{id}/project` | JWT EDITOR+ / PARTNER (ses formations) | Banque de questions, consigne du projet final (JSON brut) |
| GET/PUT/POST | `/api/v1/admin/sessions/{id}/tracking`, `/lives/{liveId}/attendance`, `/learners/{lid}/project/files`, `/project/review` | JWT ADMIN / TRAINER | Suivi de session, appel, correction du projet (JSON brut) |
| GET/POST/DELETE | `/api/v1/learner/formations/{id}/evaluations`, `/quizzes/{id}/attempts`, `/quiz-attempts/{id}/submit`, `/formations/{id}/project/files` | JWT LEARNER | Quiz et projet final (JSON brut) |
| GET | `/api/v1/learner/dashboard`, `/formations/{id}/course` ; PUT `/lessons/{id}/progress` | JWT LEARNER | Espace apprenant (JSON brut) |
| GET | `/api/v1/site-settings` | Non | Tous les contenus du site `{clé: valeur}` (ApiResponse) |
| GET/PUT/DELETE | `/api/v1/admin/site-settings` (`/{key}`) | JWT EDITOR+ | Lire / créer-remplacer / retirer un contenu du site (ApiResponse) |
| GET/POST | `/api/v1/admin/email-logs` (`/status`, `POST /test`) | JWT ADMIN (test : SUPER_ADMIN) | Journal des e-mails et diagnostic (ApiResponse) |
| GET/POST/PATCH | `/api/v1/admin/learners/**` (`/{id}/activate`, `/{id}/deactivate`, `POST /{id}/resend-invitation`) | JWT ADMIN | Comptes apprenants (ApiResponse) |
| GET/POST/PUT | `/api/v1/admin/users/**`                    | JWT SUPER_ADMIN | Comptes back-office + invitation (ApiResponse) |

---

## Workflow inscriptions bootcamp

```
1. Visiteur soumet le formulaire (POST /api/v1/registrations)
      ↓
2. Backend : reCAPTCHA → validation profil → save (status=PENDING)
      ↓
3. Notifications async :
      • notifyNewRegistration()       → Slack + email interne équipe
      • sendRegistrationPendingEmail() → email candidat (récap + instructions paiement)
         └─ Wave / OM au 78 631 04 32
         └─ Délai 24h, places restantes affichées si session connue
      ↓
4. Candidat paie et envoie capture WhatsApp au 78 631 04 32
      ↓
5. Backoffice passe statut → CONFIRMED (PATCH /api/v1/admin/registrations/{id}/status)
      ↓
6. Backend déclenche sendRegistrationConfirmedEmail() → email candidat 🎉
      └─ Place définitivement réservée + message de motivation
```

### Parcours avec acceptation et lien de paiement (lot enrollment-payment)
```
PENDING --accept--> PAYMENT_PENDING --déclaration (lien public)--> PAYMENT_TO_CONFIRM --confirm--> CONFIRMED
   \--reject--> REJECTED              \<-- paiement refusé (reject) -----------/
```
`accept` calcule le total, crée les `Payment`, envoie le lien (1re échéance). `confirm` de la **première** échéance : inscription `CONFIRMED`, place comptée (peut dépasser la capacité : un paiement reçu n'est jamais refusé), `Learner` créé ou retrouvé par e-mail (+ invitation « définir mon mot de passe »), `Enrollment` ouvert, e-mail « place confirmée ». Les échéances suivantes ne rouvrent rien. Un scheduler relance J+2 (3 relances max).
**Le flux historique `PATCH /status → CONFIRMED` reste inchangé** : il compte la place et envoie l'e-mail, mais ne crée ni compte ni accès (créer l'apprenant via `/admin/learners`). Passer à `CANCELLED`/`REJECTED` annule les échéances ouvertes.
Les e-mails de paiement reçoivent un `PaymentNotice` (valeurs simples), jamais une entité : l'envoi est `@Async`, hors transaction.

**Règle importante dans `updateStatus`** : l'email de confirmation n'est envoyé que si `oldStatus != CONFIRMED` pour éviter les doublons en cas de re-confirmation.

---

## E-mails sortants

Tout e-mail passe par `ResilientMailSender` (jamais `JavaMailSender` directement) : 3 tentatives sur les erreurs transitoires (pas sur un refus d'authentification), trace dans `email_logs` (**jamais le corps** : certains messages portent un lien de mot de passe), aucune exception remontée au métier. Types : `PAYMENT_LINK`, `PASSWORD_RESET`, `ACCOUNT_INVITATION`, `REGISTRATION_*`, `INTERNAL_*`, `NEWSLETTER_CONFIRMATION`, `MASTERCLASS_CONFIRMATION`, `TEST`.
- `app.mail.redirect-to` (`MAIL_REDIRECT_TO`) : **hors production**, envoie tout à une seule adresse de recette (l'original est rappelé dans l'objet). À laisser vide en production.
- `MAIL_FROM` : expéditeur ; vide = le compte SMTP (Gmail impose la même adresse). `MAIL_SMTP_DEBUG=true` trace SMTP complète — affiche les identifiants, dépannage local seulement.
- Diagnostic : `GET /admin/email-logs` (`?status=FAILED`), `GET /admin/email-logs/status`, `POST /admin/email-logs/test` (SUPER_ADMIN).
- Local sous Windows derrière un antivirus qui intercepte le TLS : lancer avec `scripts/run-local.ps1` (trust store Windows), sinon e-mail, Slack et reCAPTCHA échouent en `PKIX path building failed`.

## Notifications async (pattern)

```java
// Injecter dans le service :
private final NotificationService notificationService;

// Méthodes disponibles :
notificationService.notifyNewRegistration(registration);         // Slack + email interne
notificationService.sendRegistrationPendingEmail(registration);  // Email candidat PENDING
notificationService.sendRegistrationConfirmedEmail(registration);// Email candidat CONFIRMED
notificationService.notifyNewContactMessage(contact);            // Slack + email interne
notificationService.notifyPasswordResetEmail(to, link, minutes); // Email reset mdp
```

Toutes les méthodes sont `@Async` sauf `notifyPasswordResetEmail`.

---

## Config (application.yml) — variables d'env
```
DB_URL, DB_USER, DB_PASSWORD
JWT_SECRET, JWT_EXPIRATION (défaut 86400000 ms = 24h — ATTENTION: en millisecondes)
CORS_ALLOWED_ORIGINS (défaut localhost:5173,3000,8080)
MINIO_ENDPOINT, MINIO_ROOT_USER, MINIO_ROOT_PASSWORD, MINIO_BUCKET (défaut "media")
SLACK_WEBHOOK_URL
MAIL_HOST (défaut smtp.gmail.com), MAIL_PORT (défaut 587)
MAIL_USERNAME, MAIL_PASSWORD
MAIL_FROM (défaut noreply@model-technologie.com)
NOTIFICATION_EMAIL_TO (défaut business.modeltech@gmail.com)
SERVER_PORT (défaut 8080)
SPRING_PROFILES_ACTIVE (défaut dev)
RECAPTCHA_SECRET_KEY
```

---

## NE PAS FAIRE

1. **PAS de @Builder sur les entités** — Lombok @Builder ne gère pas les champs hérités de BaseEntity, MapStruct échoue. Utiliser `@Getter @Setter @NoArgsConstructor`.
2. **PAS de @Builder.Default sur les entités** — même raison.
3. **PAS de hard delete** — toujours soft delete via `entity.setDeleted(true); entity.setDeletedAt(LocalDateTime.now()); entity.setDeletedBy("system");`
4. **PAS de Java 25** — incompatible avec Lombok 1.18.30. Toujours utiliser Temurin 17 via Makefile.
5. **PAS de `isDeleted` dans @Mapping ignore** — MapStruct ne génère pas de setter pour le champ boolean `isDeleted` (préfixe `is`). Laisser commenté (`// @Mapping(target = "isDeleted", ignore = true)`).
6. **PAS de `ddl-auto: update`** — Flyway gère le schéma, JPA est en mode `validate`.
7. **PAS de FetchType.EAGER** sur les relations — toujours LAZY.
8. **PAS de @AllArgsConstructor sur les entités** — réservé aux DTOs.
9. **PAS de `ResponseEntity<Page<XxxResponse>>` brut** — toujours envelopper dans `ApiResponse.page(...)`.
10. **JWT_EXPIRATION en millisecondes** — `3600` = 3,6 secondes. Valeur correcte : `86400000` (24h) ou `3600000` (1h).

---

## TEMPLATE COPIER-COLLER : nouveau module

Remplacer `Xxx` par le nom de l'entité, `xxx` par le chemin URL, `<module>` par le module.

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

    // ... champs métier
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
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Créer un xxx")
public class CreateXxxRequest {
    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255)
    private String title;
    // ... champs métier (PAS id, PAS audit fields)
}

// --- UpdateXxxRequest.java ---
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "MAJ un xxx (tous champs optionnels)")
public class UpdateXxxRequest {
    @Size(max = 255)
    private String title;
    // ... mêmes champs que Create, SANS @NotBlank
}

// --- XxxResponse.java ---
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class XxxResponse {
    private UUID id;
    private String title;
    // ... champs métier
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
```

### 4. Mapper (avec TOUS les @Mapping ignore BaseEntity)
```java
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface XxxMapper {

    XxxResponse toResponse(Xxx entity);
    List<XxxResponse> toResponseList(List<Xxx> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    // @Mapping(target = "isDeleted", ignore = true)  // NE PAS DÉCOMMENTER — boolean prefix
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
@Service @RequiredArgsConstructor @Slf4j @Transactional(readOnly = true)
public class XxxServiceImpl implements XxxService {

    private final XxxRepository xxxRepository;
    private final XxxMapper xxxMapper;

    @Override @Transactional
    public XxxResponse create(CreateXxxRequest request) {
        log.info("Création xxx: {}", request.getTitle());
        return xxxMapper.toResponse(xxxRepository.save(xxxMapper.toEntity(request)));
    }

    @Override
    public Page<XxxResponse> findAllForAdmin(Pageable pageable) {
        return xxxRepository.findAllByIsDeletedFalse(pageable).map(xxxMapper::toResponse);
    }

    @Override
    public XxxResponse findByIdForAdmin(UUID id) {
        return xxxRepository.findByIdAndIsDeletedFalse(id)
                .map(xxxMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Xxx", "id", id));
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
                .stream().map(xxxMapper::toResponse).toList();
    }

    @Override
    public XxxResponse findPublishedById(UUID id) {
        return xxxRepository.findByIdAndPublishedTrueAndIsDeletedFalse(id)
                .map(xxxMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Xxx", "id", id));
    }
}
```

### 7. Controllers
```java
// --- PublicXxxController.java ---
@RestController
@RequestMapping("/api/v1/xxx")
@RequiredArgsConstructor
@Tag(name = "Public - Xxx")
public class PublicXxxController {
    private final XxxService xxxService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<XxxResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Xxx publiés", xxxService.findAllPublished()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<XxxResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Xxx trouvé", xxxService.findPublishedById(id)));
    }
}

// --- AdminXxxController.java ---
@RestController
@RequestMapping("/api/v1/admin/xxx")
@RequiredArgsConstructor
@Tag(name = "Admin - Xxx")
public class AdminXxxController {
    private final XxxService xxxService;

    @PostMapping
    public ResponseEntity<ApiResponse<XxxResponse>> create(@Valid @RequestBody CreateXxxRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Xxx créé", xxxService.create(req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<XxxResponse>>> getAll(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<XxxResponse> page = xxxService.findAllForAdmin(pageable);
        return ResponseEntity.ok(ApiResponse.page(page.getTotalElements() + " élément(s)", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<XxxResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Xxx trouvé", xxxService.findByIdForAdmin(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<XxxResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateXxxRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Xxx mis à jour", xxxService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        xxxService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 8. Migration Flyway (prochaine : V11)
```sql
-- V11__Create_xxx_table.sql
CREATE TABLE xxx_table_name (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    -- ... champs métier ...
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

## Build
```bash
.\scripts\run-local.ps1 [-Build]   # backend local (JAR) avec logs dans le terminal, base/MinIO de Docker
make compile     # compile (JAVA_HOME force Temurin 17)
make run         # spring-boot:run profil dev
make test        # tests (idem ./mvnw test) — les tests d'intégration utilisent Testcontainers : Docker doit tourner
                 # (si Maven refuse un certificat : MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL")
make up          # docker-compose up (db + minio + adminer)
make up-all      # docker-compose up --build (tout)
```