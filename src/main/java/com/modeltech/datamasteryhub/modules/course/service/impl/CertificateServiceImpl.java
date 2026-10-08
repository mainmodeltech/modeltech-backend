package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.course.dto.CertificatePayloads;
import com.modeltech.datamasteryhub.modules.course.entity.Certificate;
import com.modeltech.datamasteryhub.modules.course.repository.CertificateRepository;
import com.modeltech.datamasteryhub.modules.course.service.CertificateEligibility;
import com.modeltech.datamasteryhub.modules.course.service.CertificateIssuedEvent;
import com.modeltech.datamasteryhub.modules.course.service.CertificatePdfGenerator;
import com.modeltech.datamasteryhub.modules.course.service.CertificateService;
import com.modeltech.datamasteryhub.modules.notification.service.CertificateNotice;
import com.modeltech.datamasteryhub.modules.notification.service.NotificationService;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CertificateServiceImpl implements CertificateService {

    private static final String SYSTEM = "système";
    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // sans 0/O/1/I : lisible à voix haute
    private static final Set<String> STOP_WORDS = Set.of("de", "du", "des", "la", "le", "les", "et", "au", "aux",
            "pour", "en", "sur", "un", "une", "ses", "son", "avec", "dans", "par");

    private final SecureRandom random = new SecureRandom();

    private final CertificateRepository certificateRepository;
    private final LearnerRepository learnerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BootcampSessionRepository sessionRepository;
    private final CertificateEligibility eligibility;
    private final CertificatePdfGenerator pdfGenerator;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.certificate.signatory-name:Patrick Lionnel DOOKO}")
    private String signatoryName;

    @Value("${app.certificate.signatory-title:Gérant, Model Technologie}")
    private String signatoryTitle;

    @Value("${app.certificate.issuer:Model Technologie}")
    private String issuer;

    @Value("${app.certificate.id-prefix:MT}")
    private String idPrefix;

    @Value("${app.certificate.linkedin-organization-id:103600105}")
    private String linkedInOrganizationId;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.frontend.certificate-path:/certificats}")
    private String certificatePath;

    // =========================================================================
    //  DÉLIVRANCE
    // =========================================================================

    @Override
    @Transactional
    public Optional<String> issueIfEligible(UUID learnerId, UUID bootcampId) {
        // Jamais de redélivrance automatique : un certificat révoqué ne revient que par une décision humaine
        if (certificateRepository.existsByLearnerIdAndBootcampIdAndIsDeletedFalse(learnerId, bootcampId)) {
            return Optional.empty();
        }
        Learner learner = learnerRepository.findByIdAndIsDeletedFalse(learnerId).orElse(null);
        if (learner == null) return Optional.empty();
        Enrollment enrollment = enrollmentRepository.findAccessibleByLearner(learnerId).stream()
                .filter(e -> e.getRegistration().getBootcamp().getId().equals(bootcampId)).findFirst().orElse(null);
        if (enrollment == null) return Optional.empty();

        Bootcamp bootcamp = enrollment.getRegistration().getBootcamp();
        CertificateEligibility.Evaluation evaluation = eligibility.evaluate(learner, bootcamp, enrollment.getSession());
        if (!evaluation.eligible()) return Optional.empty();

        return Optional.of(issue(learner, bootcamp, enrollment.getSession(), evaluation.includesProject(), SYSTEM, false, null).getPublicId());
    }

    @Override
    @Transactional
    public CertificatePayloads.AdminCertificate issueManually(UUID sessionId, UUID learnerId, CertificatePayloads.IssueRequest request,
                                                              String actorEmail, Collection<String> actorRoles) {
        BootcampSession session = sessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));
        Enrollment enrollment = enrollmentRepository.findActiveBySession(sessionId).stream()
                .filter(e -> e.getLearner().getId().equals(learnerId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Apprenant", "id", learnerId));
        Bootcamp bootcamp = session.getBootcamp();

        if (certificateRepository.findByLearnerIdAndBootcampIdAndStatusAndIsDeletedFalse(learnerId, bootcamp.getId(), Certificate.VALID).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet apprenant a déjà un certificat valide pour cette formation.");
        }

        CertificateEligibility.Evaluation evaluation = eligibility.evaluate(enrollment.getLearner(), bootcamp, session);
        boolean force = Boolean.TRUE.equals(request.getForce());
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (!evaluation.eligible()) {
            if (!force) {
                String missing = evaluation.conditions().stream().filter(c -> !c.isMet())
                        .map(c -> c.getLabel() + " (" + c.getValueLabel() + ")").collect(Collectors.joining(", "));
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Conditions non remplies : " + (missing.isEmpty() ? "programme vide" : missing)
                                + ". Utilisez force avec un motif pour déroger.");
            }
            if (!actorRoles.contains(RoleNames.ADMIN) && !actorRoles.contains(RoleNames.SUPER_ADMIN)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seule l'administration peut délivrer un certificat par dérogation.");
            }
            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif de la dérogation est obligatoire.");
            }
        }
        boolean forced = force && !evaluation.eligible();
        Certificate certificate = issue(enrollment.getLearner(), bootcamp, session, evaluation.includesProject(),
                actorEmail, forced, forced ? reason : null);
        return toAdmin(certificate);
    }

    /** Photographie les informations du certificat, lui attribue son numéro et programme l'e-mail après validation de la transaction. */
    private Certificate issue(Learner learner, Bootcamp bootcamp, BootcampSession session, boolean includesProject,
                              String issuedBy, boolean forced, String forceReason) {
        LocalDateTime now = LocalDateTime.now();
        Certificate c = new Certificate();
        c.setPublicId(nextPublicId(bootcamp, now));
        c.setLearner(learner);
        c.setBootcamp(bootcamp);
        c.setSession(session);
        c.setRecipientName(learner.getFullName());
        c.setFormationTitle(bootcamp.getTitle());
        c.setDurationLabel(bootcamp.getDuration());
        c.setSkills(bootcamp.getBenefits() == null ? List.of()
                : bootcamp.getBenefits().stream().filter(b -> b != null && !b.isBlank()).map(String::trim).limit(6).toList());
        c.setIncludesProject(includesProject);
        c.setSignatoryName(signatoryName);
        c.setSignatoryTitle(signatoryTitle);
        c.setTrainerName(null);
        c.setIssuedAt(now);
        c.setIssuedBy(issuedBy);
        c.setForced(forced);
        c.setForceReason(forceReason);
        c.setStatus(Certificate.VALID);
        Certificate saved = certificateRepository.save(c);
        log.info("Certificat {} délivré à {} pour « {} » ({}{})", saved.getPublicId(), learner.getEmail(), bootcamp.getTitle(),
                issuedBy, forced ? ", dérogation" : "");
        events.publishEvent(new CertificateIssuedEvent(saved.getPublicId()));
        return saved;
    }

    // =========================================================================
    //  RÉVOCATION, RENVOI, LISTES
    // =========================================================================

    @Override
    @Transactional
    public CertificatePayloads.AdminCertificate revoke(String publicId, String reason, String actorEmail) {
        Certificate c = requireByPublicId(publicId);
        if (!c.isValid()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce certificat est déjà révoqué.");
        c.setStatus(Certificate.REVOKED);
        c.setRevokedAt(LocalDateTime.now());
        c.setRevokedReason(reason.trim());
        log.info("Certificat {} révoqué par {} : {}", publicId, actorEmail, reason);
        return toAdmin(certificateRepository.save(c));
    }

    @Override
    public void resend(String publicId) {
        Certificate c = requireByPublicId(publicId);
        if (!c.isValid()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce certificat est révoqué.");
        notifyIssued(publicId);
    }

    @Override
    public Page<CertificatePayloads.AdminCertificate> findAllForAdmin(String status, Pageable pageable) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return certificateRepository.search(normalized, pageable).map(this::toAdmin);
    }

    // =========================================================================
    //  PUBLIC ET APPRENANT
    // =========================================================================

    @Override
    public CertificatePayloads.PublicCertificate verify(String publicId) {
        Certificate c = requireByPublicId(publicId);
        return CertificatePayloads.PublicCertificate.builder()
                .publicId(c.getPublicId())
                .status(c.getStatus())
                .recipientName(c.getRecipientName())
                .formationTitle(c.getFormationTitle())
                .durationLabel(c.getDurationLabel())
                .skills(c.getSkills())
                .includesProject(c.isIncludesProject())
                .issuedAt(c.getIssuedAt())
                .issuer(issuer)
                .signatoryName(c.getSignatoryName())
                .signatoryTitle(c.getSignatoryTitle())
                .revokedAt(c.getRevokedAt())
                .build();
    }

    @Override
    public byte[] pdf(String publicId) {
        Certificate c = requireByPublicId(publicId);
        if (!c.isValid()) throw new ResponseStatusException(HttpStatus.GONE, "Ce certificat a été révoqué.");
        return render(c);
    }

    @Override
    public List<CertificatePayloads.LearnerCertificate> findForLearner(String learnerEmail) {
        Learner learner = learnerRepository.findByEmailIgnoreCaseAndIsDeletedFalse(learnerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Espace réservé aux apprenants."));
        return certificateRepository.findAllByLearnerIdAndIsDeletedFalseOrderByIssuedAtDesc(learner.getId()).stream()
                .map(c -> CertificatePayloads.LearnerCertificate.builder()
                        .publicId(c.getPublicId())
                        .formationId(c.getBootcamp().getId().toString())
                        .formationTitle(c.getFormationTitle())
                        .issuedAt(c.getIssuedAt())
                        .status(c.getStatus())
                        .verifyUrl(verifyUrl(c.getPublicId()))
                        .pdfPath("/api/v1/certificates/" + c.getPublicId() + "/pdf")
                        .linkedInUrl(linkedInUrl(c))
                        .build())
                .toList();
    }

    // =========================================================================
    //  E-MAIL ET BALAYAGE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)   // lecture seule : participe à la transaction déjà validée quand il est appelé après commit
    public void notifyIssued(String publicId) {
        Certificate c = requireByPublicId(publicId);
        Learner learner = c.getLearner();
        notificationService.sendCertificateReadyEmail(new CertificateNotice(
                learner.getEmail(), learner.getFirstName(), c.getFormationTitle(), c.getPublicId(),
                verifyUrl(c.getPublicId()), linkedInUrl(c), render(c)));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int sweep() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Le balayage repart des paires (apprenant, formation), chacune dans sa propre transaction
        List<UUID[]> pairs = tx.execute(status -> enrollmentRepository.findAllForCertificateSweep().stream()
                .map(e -> new UUID[]{e.getLearner().getId(), e.getRegistration().getBootcamp().getId()}).toList());
        int issued = 0;
        for (UUID[] pair : Objects.requireNonNullElse(pairs, List.<UUID[]>of())) {
            try {
                Optional<String> result = tx.execute(status -> issueIfEligible(pair[0], pair[1]));
                if (result != null && result.isPresent()) issued++;
            } catch (RuntimeException e) {
                log.warn("Balayage des certificats : échec pour l'apprenant {} / formation {} : {}", pair[0], pair[1], e.getMessage());
            }
        }
        if (issued > 0) log.info("Balayage des certificats : {} certificat(s) délivré(s)", issued);
        return issued;
    }

    // =========================================================================
    //  PRIVÉ
    // =========================================================================

    private Certificate requireByPublicId(String publicId) {
        String normalized = publicId == null ? "" : publicId.trim().toUpperCase(Locale.ROOT);
        return certificateRepository.findByPublicIdAndIsDeletedFalse(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Certificat introuvable."));
    }

    private byte[] render(Certificate c) {
        return pdfGenerator.generate(new CertificatePdfGenerator.CertificateDocument(
                c.getPublicId(), c.getRecipientName(), c.getFormationTitle(), c.getDurationLabel(),
                c.getSkills(), c.isIncludesProject(), c.getSignatoryName(), c.getSignatoryTitle(),
                c.getTrainerName(), c.getIssuedAt(), verifyUrl(c.getPublicId())));
    }

    private String verifyUrl(String publicId) {
        return frontendUrl.replaceAll("/+$", "") + certificatePath + "/" + publicId;
    }

    /** Lien « Ajouter à mon profil LinkedIn » (certification avec numéro et lien de vérification). */
    private String linkedInUrl(Certificate c) {
        return "https://www.linkedin.com/profile/add?startTask=CERTIFICATION_NAME"
                + "&name=" + enc(c.getFormationTitle())
                + "&organizationId=" + enc(linkedInOrganizationId)
                + "&issueYear=" + c.getIssuedAt().getYear()
                + "&issueMonth=" + c.getIssuedAt().getMonthValue()
                + "&certUrl=" + enc(verifyUrl(c.getPublicId()))
                + "&certId=" + enc(c.getPublicId());
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** {@code MT-2026-VBA-00042-K7QX} : suite continue, code de la formation, suffixe aléatoire (empêche d'énumérer les certificats). */
    private String nextPublicId(Bootcamp bootcamp, LocalDateTime now) {
        Long number = jdbc.queryForObject("SELECT nextval('certificate_number_seq')", Long.class);
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 4; i++) suffix.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        return "%s-%d-%s-%05d-%s".formatted(idPrefix, now.getYear(), formationCode(bootcamp), number, suffix);
    }

    /** Code saisi sur la formation, sinon un mot en majuscules du titre (VBA, SQL…), sinon les initiales des premiers mots. */
    String formationCode(Bootcamp bootcamp) {
        if (bootcamp.getCertificateCode() != null && !bootcamp.getCertificateCode().isBlank()) {
            return bootcamp.getCertificateCode();
        }
        String ascii = Normalizer.normalize(bootcamp.getTitle() == null ? "" : bootcamp.getTitle(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        List<String> words = Arrays.stream(ascii.split("[^A-Za-z0-9]+")).filter(w -> w.length() >= 2).toList();
        Optional<String> acronym = words.stream().filter(w -> w.length() >= 3 && w.length() <= 6 && w.equals(w.toUpperCase(Locale.ROOT))
                && w.chars().allMatch(Character::isLetter)).findFirst();
        if (acronym.isPresent()) return acronym.get();
        String initials = words.stream().filter(w -> !STOP_WORDS.contains(w.toLowerCase(Locale.ROOT)))
                .limit(3).map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT)).collect(Collectors.joining());
        return initials.length() >= 2 ? initials : "FOR";
    }

    private CertificatePayloads.AdminCertificate toAdmin(Certificate c) {
        return CertificatePayloads.AdminCertificate.builder()
                .id(c.getId())
                .publicId(c.getPublicId())
                .learnerId(c.getLearner().getId())
                .learnerName(c.getRecipientName())
                .learnerEmail(c.getLearner().getEmail())
                .formationId(c.getBootcamp().getId())
                .formationTitle(c.getFormationTitle())
                .sessionId(c.getSession() != null ? c.getSession().getId() : null)
                .sessionName(c.getSession() != null ? c.getSession().getSessionName() : null)
                .issuedAt(c.getIssuedAt())
                .issuedBy(c.getIssuedBy())
                .forced(c.isForced())
                .forceReason(c.getForceReason())
                .status(c.getStatus())
                .revokedAt(c.getRevokedAt())
                .revokedReason(c.getRevokedReason())
                .verifyUrl(verifyUrl(c.getPublicId()))
                .build();
    }
}
