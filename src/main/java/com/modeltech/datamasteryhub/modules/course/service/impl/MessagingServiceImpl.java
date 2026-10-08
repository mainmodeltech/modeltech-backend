package com.modeltech.datamasteryhub.modules.course.service.impl;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.course.dto.MessagingPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CourseLesson;
import com.modeltech.datamasteryhub.modules.course.entity.LessonQuestion;
import com.modeltech.datamasteryhub.modules.course.entity.LiveReminder;
import com.modeltech.datamasteryhub.modules.course.entity.SessionMessage;
import com.modeltech.datamasteryhub.modules.course.enums.LessonStatus;
import com.modeltech.datamasteryhub.modules.course.repository.CourseLessonRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LessonQuestionRepository;
import com.modeltech.datamasteryhub.modules.course.repository.LiveReminderRepository;
import com.modeltech.datamasteryhub.modules.course.repository.SessionMessageRepository;
import com.modeltech.datamasteryhub.modules.course.service.LearnerAccess;
import com.modeltech.datamasteryhub.modules.course.service.MessagingService;
import com.modeltech.datamasteryhub.modules.course.service.SessionAccessPolicy;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Enrollment;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampSessionRepository;
import com.modeltech.datamasteryhub.modules.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MessagingServiceImpl implements MessagingService {

    private static final int MAX_RECIPIENTS = 300;
    private static final int MAX_OPEN_QUESTIONS_PER_LESSON = 5;

    private final CourseLessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BootcampSessionRepository sessionRepository;
    private final LiveReminderRepository reminderRepository;
    private final SessionMessageRepository messageRepository;
    private final LessonQuestionRepository questionRepository;
    private final AdminUserRepository adminUserRepository;
    private final MessageDispatcher dispatcher;
    private final SessionAccessPolicy sessionAccess;
    private final LearnerAccess learnerAccess;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.brand.name:Model Technologie}")
    private String brandName;

    @Value("${app.notifications.email.to:}")
    private String teamEmail;

    // =========================================================================
    //  RAPPELS DE LIVE
    // =========================================================================

    @Override
    @Transactional
    public int sendDueLiveReminders(LocalDateTime now) {
        int sent = 0;
        for (CourseLesson live : lessonRepository.findLivesBetween(now, now.plusHours(24))) {
            long minutesLeft = Duration.between(now, live.getLiveAt()).toMinutes();
            // Un seul rappel est dû à un instant donné : « dans 1 h » s'il reste moins d'une heure, sinon « 24 h avant »
            String kind = minutesLeft <= 60 ? LiveReminder.H1 : LiveReminder.H24;

            Map<UUID, List<Enrollment>> bySession = enrollmentRepository
                    .findActiveByBootcamp(live.getModule().getBootcamp().getId()).stream()
                    .filter(e -> e.getAccessStartsAt() == null || !learnerAccess.today().isBefore(e.getAccessStartsAt()))
                    .collect(Collectors.groupingBy(e -> e.getSession().getId(), LinkedHashMap::new, Collectors.toList()));

            for (Map.Entry<UUID, List<Enrollment>> group : bySession.entrySet()) {
                if (reminderRepository.existsByLessonIdAndSessionIdAndKind(live.getId(), group.getKey(), kind)) continue;
                BootcampSession session = group.getValue().get(0).getSession();
                int delivered = 0;
                for (Enrollment e : group.getValue()) {
                    if (dispatcher.send(reminderMessage(live, session, e.getLearner(), kind, now))) delivered++;
                }
                LiveReminder record = new LiveReminder();
                record.setLesson(live);
                record.setSession(session);
                record.setKind(kind);
                record.setRecipients(group.getValue().size());
                record.setSentAt(now);
                reminderRepository.save(record);
                sent += delivered;
                log.info("Rappel {} du live « {} » (session {}) : {}/{} apprenant(s)", kind, live.getTitle(),
                        session.getSessionName(), delivered, group.getValue().size());
            }
        }
        return sent;
    }

    private OutboundMessage reminderMessage(CourseLesson live, BootcampSession session, Learner learner, String kind, LocalDateTime now) {
        String time = "%02dh%02d".formatted(live.getLiveAt().getHour(), live.getLiveAt().getMinute());
        boolean today = live.getLiveAt().toLocalDate().equals(now.toLocalDate());
        String subject = LiveReminder.H1.equals(kind)
                ? "Dans 1 heure : live « " + live.getTitle() + " »"
                : "Rappel : live « " + live.getTitle() + " » " + (today ? "aujourd'hui" : "demain") + " à " + time;
        String link = live.getLiveUrl() != null && !live.getLiveUrl().isBlank()
                ? "Rejoindre le live : " + live.getLiveUrl()
                : "Le lien de connexion est disponible dans votre espace : " + base() + "/espace";
        String body = """
                Bonjour %s,

                Votre live « %s » (%s · %s) a lieu %s à %s.

                %s

                À tout à l'heure !
                — L'équipe %s
                """.formatted(learner.getFirstName(), live.getTitle(), live.getModule().getBootcamp().getTitle(),
                session.getSessionName(), LiveReminder.H1.equals(kind) ? "dans une heure" : (today ? "aujourd'hui" : "demain"),
                time, link, brandName);
        return OutboundMessage.of("LIVE_REMINDER", learner.getEmail(), learner.getFullName(), subject, body);
    }

    // =========================================================================
    //  MESSAGES À UNE SESSION
    // =========================================================================

    @Override
    @Transactional
    public MessagingPayloads.SessionMessageResponse sendToSession(UUID sessionId, MessagingPayloads.SessionMessageRequest request,
                                                                  String actorEmail, Collection<String> actorRoles) {
        BootcampSession session = requireSession(sessionId, actorEmail, actorRoles);
        List<Enrollment> enrollments = enrollmentRepository.findActiveBySession(sessionId);

        List<Enrollment> targets = enrollments;
        if (request.getLearnerIds() != null && !request.getLearnerIds().isEmpty()) {
            Set<UUID> wanted = new HashSet<>(request.getLearnerIds());
            targets = enrollments.stream().filter(e -> wanted.contains(e.getLearner().getId())).toList();
            if (targets.size() != wanted.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un destinataire n'est pas inscrit à cette session.");
            }
        }
        if (targets.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun apprenant à qui écrire dans cette session.");
        if (targets.size() > MAX_RECIPIENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "300 destinataires maximum par message.");
        }

        String senderName = adminUserRepository.findByEmailAndIsDeletedFalse(actorEmail).map(a -> a.getFullName()).orElse(actorEmail);
        String replyTo = actorEmail != null && actorEmail.contains("@") ? actorEmail : null;
        String footer = "\n\n— " + senderName + ", " + brandName + "\nVous recevez ce message car vous suivez « "
                + session.getBootcamp().getTitle() + " » (" + session.getSessionName() + ").";
        List<OutboundMessage> messages = targets.stream()
                .map(e -> new OutboundMessage("SESSION_MESSAGE", e.getLearner().getEmail(), null, e.getLearner().getFullName(),
                        request.getSubject().trim(), "Bonjour " + e.getLearner().getFirstName() + ",\n\n" + request.getBody().trim() + footer, replyTo))
                .toList();

        SessionMessage record = new SessionMessage();
        record.setSession(session);
        record.setSubject(request.getSubject().trim());
        record.setBody(request.getBody().trim());
        record.setSentBy(actorEmail);
        record.setRecipientCount(messages.size());
        SessionMessage saved = messageRepository.save(record);

        UUID messageId = saved.getId();
        dispatcher.sendAll(messages, delivered -> messageRepository.findById(messageId).ifPresent(m -> {
            m.setDeliveredCount(delivered);
            messageRepository.save(m);
        }));
        log.info("Message « {} » à la session {} par {} : {} destinataire(s)", saved.getSubject(), sessionId, actorEmail, messages.size());
        return toResponse(saved);
    }

    @Override
    public List<MessagingPayloads.SessionMessageResponse> history(UUID sessionId, String actorEmail, Collection<String> actorRoles) {
        requireSession(sessionId, actorEmail, actorRoles);
        return messageRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtDesc(sessionId).stream().map(this::toResponse).toList();
    }

    // =========================================================================
    //  QUESTIONS
    // =========================================================================

    @Override
    @Transactional
    public MessagingPayloads.LearnerQuestion ask(String learnerEmail, UUID lessonId, String text) {
        Learner learner = learnerAccess.requireLearner(learnerEmail);
        CourseLesson lesson = requireVisibleLesson(lessonId);
        Enrollment enrollment = learnerAccess.requireAccess(learner, lesson.getModule().getBootcamp().getId());
        if (questionRepository.countByLearnerIdAndLessonIdAndAnswerIsNullAndIsDeletedFalse(learner.getId(), lessonId) >= MAX_OPEN_QUESTIONS_PER_LESSON) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous avez déjà " + MAX_OPEN_QUESTIONS_PER_LESSON + " questions en attente de réponse sur cette leçon.");
        }

        LessonQuestion q = new LessonQuestion();
        q.setLearner(learner);
        q.setLesson(lesson);
        q.setSession(enrollment.getSession());
        q.setQuestion(text.trim());
        LessonQuestion saved = questionRepository.save(q);

        String to = enrollment.getSession() != null && enrollment.getSession().getTrainer() != null
                ? enrollment.getSession().getTrainer().getEmail() : teamEmail;
        if (to != null && !to.isBlank()) {
            String where = enrollment.getSession() != null ? "/admin/sessions/" + enrollment.getSession().getId() : "/admin";
            dispatcher.sendAll(List.of(OutboundMessage.of("LESSON_QUESTION", to, null,
                    "Question d'un apprenant — " + lesson.getTitle(),
                    "%s pose une question sur la leçon « %s » (%s) :\n\n%s\n\nRépondre : %s%s".formatted(
                            learner.getFullName(), lesson.getTitle(), lesson.getModule().getBootcamp().getTitle(),
                            saved.getQuestion(), base(), where))), null);
        }
        return toLearnerView(saved);
    }

    @Override
    public List<MessagingPayloads.LearnerQuestion> myQuestions(String learnerEmail, UUID lessonId) {
        Learner learner = learnerAccess.requireLearner(learnerEmail);
        CourseLesson lesson = requireVisibleLesson(lessonId);
        learnerAccess.requireAccess(learner, lesson.getModule().getBootcamp().getId());
        return questionRepository.findAllByLearnerIdAndLessonIdAndIsDeletedFalseOrderByCreatedAtAsc(learner.getId(), lessonId)
                .stream().map(this::toLearnerView).toList();
    }

    @Override
    public List<MessagingPayloads.SessionQuestion> sessionQuestions(UUID sessionId, boolean onlyOpen,
                                                                    String actorEmail, Collection<String> actorRoles) {
        requireSession(sessionId, actorEmail, actorRoles);
        return questionRepository.findForSession(sessionId, onlyOpen).stream().map(this::toSessionView).toList();
    }

    @Override
    @Transactional
    public MessagingPayloads.SessionQuestion answer(UUID sessionId, UUID questionId, String answer,
                                                    String actorEmail, Collection<String> actorRoles) {
        requireSession(sessionId, actorEmail, actorRoles);
        LessonQuestion q = questionRepository.findByIdAndIsDeletedFalse(questionId)
                .filter(x -> x.getSession() != null && x.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", questionId));
        if (q.isAnswered()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette question a déjà reçu une réponse.");

        String author = adminUserRepository.findByEmailAndIsDeletedFalse(actorEmail).map(a -> a.getFullName()).orElse(actorEmail);
        q.setAnswer(answer.trim());
        q.setAnsweredBy(author);
        q.setAnsweredAt(LocalDateTime.now());
        LessonQuestion saved = questionRepository.save(q);

        dispatcher.sendAll(List.of(new OutboundMessage("QUESTION_ANSWER", q.getLearner().getEmail(), null, q.getLearner().getFullName(),
                "Réponse à votre question — " + q.getLesson().getTitle(),
                "Bonjour %s,\n\nVotre question sur « %s » :\n> %s\n\nRéponse de %s :\n%s\n\nRetrouvez-la dans votre espace : %s/espace\n— %s".formatted(
                        q.getLearner().getFirstName(), q.getLesson().getTitle(), q.getQuestion(), author, saved.getAnswer(), base(), brandName),
                actorEmail != null && actorEmail.contains("@") ? actorEmail : null)), null);
        return toSessionView(saved);
    }

    // =========================================================================
    //  PRIVÉ
    // =========================================================================

    private BootcampSession requireSession(UUID sessionId, String actorEmail, Collection<String> actorRoles) {
        BootcampSession session = sessionRepository.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));
        sessionAccess.require(session, actorEmail, actorRoles);
        return session;
    }

    private CourseLesson requireVisibleLesson(UUID lessonId) {
        return lessonRepository.findByIdAndIsDeletedFalse(lessonId)
                .filter(l -> !l.getModule().isDeleted() && l.getStatus() != LessonStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Leçon", "id", lessonId));
    }

    private String base() {
        return frontendUrl.replaceAll("/+$", "");
    }

    private MessagingPayloads.SessionMessageResponse toResponse(SessionMessage m) {
        return MessagingPayloads.SessionMessageResponse.builder()
                .id(m.getId()).subject(m.getSubject()).body(m.getBody()).sentBy(m.getSentBy())
                .recipientCount(m.getRecipientCount()).deliveredCount(m.getDeliveredCount()).sentAt(m.getCreatedAt()).build();
    }

    private MessagingPayloads.LearnerQuestion toLearnerView(LessonQuestion q) {
        return MessagingPayloads.LearnerQuestion.builder()
                .id(q.getId()).lessonId(q.getLesson().getId()).question(q.getQuestion()).askedAt(q.getCreatedAt())
                .answer(q.getAnswer()).answeredBy(q.getAnsweredBy()).answeredAt(q.getAnsweredAt()).build();
    }

    private MessagingPayloads.SessionQuestion toSessionView(LessonQuestion q) {
        return MessagingPayloads.SessionQuestion.builder()
                .id(q.getId()).learnerId(q.getLearner().getId()).learnerName(q.getLearner().getFullName())
                .lessonId(q.getLesson().getId()).lessonTitle(q.getLesson().getTitle()).question(q.getQuestion())
                .askedAt(q.getCreatedAt()).answer(q.getAnswer()).answeredBy(q.getAnsweredBy()).answeredAt(q.getAnsweredAt()).build();
    }
}
