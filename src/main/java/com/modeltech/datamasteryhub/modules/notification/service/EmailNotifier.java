package com.modeltech.datamasteryhub.modules.notification.service;

import com.modeltech.datamasteryhub.modules.communication.entity.ContactMessage;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Envoie un email de notification pour chaque nouvelle inscription.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotifier {

    private final JavaMailSender mailSender;

    /** Email de l'équipe interne (destinataire des notifications) */
    @Value("${app.notifications.email.to:}")
    private String internalEmail;

    /** Adresse expéditeur Spring Mail */
    @Value("${spring.mail.username:noreply@model-technologie.com}")
    private String fromEmail;

    /** Email de l'équipe interne (destinataire des notifications) */
    @Value("${app.notifications.phone.to:}")
    private String paymentPhone;

    // =========================================================================
    //  EMAIL INTERNE ÉQUIPE — Nouvelle inscription
    // =========================================================================

    public void send(Registration registration) {
        if (isDisabled()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");

            String fullName = fullName(registration);
            String bootcamp = orDash(registration.getBootcampTitle());

            h.setFrom(fromEmail);
            h.setTo(internalEmail);
            h.setSubject("📋 Nouvelle inscription — " + fullName + " — " + bootcamp);
            h.setText(buildInternalRegistrationHtml(registration, fullName, bootcamp), true);

            mailSender.send(msg);
            log.info("Email interne envoyé à {} pour l'inscription de {}", internalEmail, fullName);
        } catch (MessagingException e) {
            log.error("Erreur email interne inscription : {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    //  EMAIL CANDIDAT — Inscription reçue + instructions de paiement
    // =========================================================================

    /**
     * Envoyé au candidat juste après la sauvegarde (statut PENDING).
     * Contient :
     *   - Confirmation de réception
     *   - Récap bootcamp / session / tarif
     *   - Instructions de paiement (Wave, OM, numéro)
     *   - Nombre de places restantes si disponible
     *   - Délai de 24h pour payer
     */
    public void sendPendingConfirmationToCandidate(Registration registration) {
        if (registration.getEmail() == null || registration.getEmail().isBlank()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");

            String fullName = fullName(registration);
            String bootcamp = orDash(registration.getBootcampTitle());

            h.setFrom(fromEmail);
            h.setTo(registration.getEmail());
            h.setSubject("✅ Inscription reçue — " + bootcamp + " · Prochaine étape : paiement");
            h.setText(buildPendingCandidateHtml(registration, fullName, bootcamp), true);

            mailSender.send(msg);
            log.info("Email 'pending' envoyé au candidat {}", registration.getEmail());
        } catch (MessagingException e) {
            log.error("Erreur email 'pending' candidat {} : {}", registration.getEmail(), e.getMessage(), e);
        }
    }

    // =========================================================================
    //  EMAIL CANDIDAT — Place confirmée
    // =========================================================================

    /**
     * Envoyé au candidat quand le backoffice passe le statut à CONFIRMED.
     * Message de bienvenue chaleureux + date de début si disponible.
     */
    public void sendConfirmedToCandidate(Registration registration) {
        if (registration.getEmail() == null || registration.getEmail().isBlank()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");

            String fullName = fullName(registration);
            String bootcamp = orDash(registration.getBootcampTitle());

            h.setFrom(fromEmail);
            h.setTo(registration.getEmail());
            h.setSubject("🎉 Place confirmée ! Bienvenue dans le bootcamp " + bootcamp);
            h.setText(buildConfirmedCandidateHtml(registration, fullName, bootcamp), true);

            mailSender.send(msg);
            log.info("Email 'confirmed' envoyé au candidat {}", registration.getEmail());
        } catch (MessagingException e) {
            log.error("Erreur email 'confirmed' candidat {} : {}", registration.getEmail(), e.getMessage(), e);
        }
    }

    // =========================================================================
    //  EMAIL INTERNE ÉQUIPE — Message de contact
    // =========================================================================

    public void sendContactMessage(ContactMessage contact) {
        if (isDisabled()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");

            String fullName = contact.getFirstName() + " " + contact.getLastName();
            String sujet    = contact.getSubject() != null && !contact.getSubject().isBlank()
                    ? contact.getSubject() : "Sans objet";

            h.setFrom(fromEmail);
            h.setTo(internalEmail);
            h.setReplyTo(contact.getEmail());
            h.setSubject("✉️ Nouveau message de contact — " + fullName + " — " + sujet);
            h.setText(buildContactHtml(contact, fullName, sujet), true);

            mailSender.send(msg);
            log.info("Email (contact) envoyé à {} pour {}", internalEmail, fullName);
        } catch (MessagingException e) {
            log.error("Erreur email contact : {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    //  EMAIL AUTH — Reset mot de passe
    // =========================================================================

    public void sendPasswordResetEmail(String to, String resetLink, int expiresMinutes) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject("[Model Technologie] Réinitialisation de votre mot de passe");
            msg.setText("""
                    Bonjour,
                    
                    Vous avez demandé la réinitialisation de votre mot de passe.
                    
                    Cliquez sur le lien ci-dessous pour définir un nouveau mot de passe :
                    %s
                    
                    Ce lien est valable %d minutes.
                    
                    Si vous n'avez pas fait cette demande, ignorez cet email.
                    
                    — L'équipe Model Technologie
                    """.formatted(resetLink, expiresMinutes));
            mailSender.send(msg);
            log.info("Email reset envoyé à {}", to);
        } catch (Exception e) {
            log.error("Erreur email reset pour {} : {}", to, e.getMessage());
        }
    }

    // =========================================================================
    //  HTML BUILDERS
    // =========================================================================

    /** Email interne équipe — récap complet de l'inscription */
    private String buildInternalRegistrationHtml(Registration reg, String fullName, String bootcamp) {
        String date = formatDate(reg);
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#333">
                  <div style="background:linear-gradient(135deg,#1e3a5f 0%%,#0ea5e9 100%%);padding:24px;border-radius:12px 12px 0 0">
                    <h1 style="color:white;margin:0;font-size:20px">📋 Nouvelle inscription</h1>
                    <p style="color:rgba(255,255,255,.8);margin:8px 0 0">Model Technologie — Data Mastery Hub</p>
                  </div>
                  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px">
                    <table style="width:100%%;border-collapse:collapse">
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280;width:140px">Nom complet</td><td style="padding:8px 0">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Email</td><td style="padding:8px 0"><a href="mailto:%s" style="color:#0ea5e9">%s</a></td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Téléphone</td><td style="padding:8px 0">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Pays</td><td style="padding:8px 0">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Profil</td><td style="padding:8px 0">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Bootcamp</td><td style="padding:8px 0;font-weight:600;color:#1e3a5f">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Session</td><td style="padding:8px 0">%s</td></tr>
                      %s
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Entreprise</td><td style="padding:8px 0">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Poste</td><td style="padding:8px 0">%s</td></tr>
                      %s
                    </table>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:16px 0">
                    <p style="font-size:13px;color:#9ca3af;margin:0">Reçu le %s — Statut : En attente</p>
                  </div>
                </body></html>
                """.formatted(
                e(fullName),
                e(reg.getEmail()), e(reg.getEmail()),
                e(orDash(reg.getPhone())),
                e(orDash(reg.getCountry())),
                e(orDash(reg.getProfile() != null ? reg.getProfile().name() : null)),
                e(bootcamp),
                e(orDash(reg.getSessionName())),
                promoRow(reg),
                e(orDash(reg.getCompany())),
                e(orDash(reg.getPosition())),
                messageRow(reg),
                date
        );
    }

    /**
     * Email candidat — statut PENDING.
     * Contient le récap + instructions de paiement + places restantes.
     */
    private String buildPendingCandidateHtml(Registration reg, String fullName, String bootcamp) {
        String session     = orDash(reg.getSessionName());
        String phone       = orDash(reg.getPhone());
        String date        = formatDate(reg);
        String placesInfo  = buildPlacesInfo(reg);
        String promoInfo   = reg.getDiscountPercent() != null && reg.getDiscountPercent() > 0
                ? "<p style=\"color:#16a34a;font-size:14px;margin:0 0 8px\">🏷️ Code promo <strong>%s</strong> appliqué : réduction de <strong>%d%%</strong></p>"
                .formatted(e(reg.getPromoCodeUsed()), reg.getDiscountPercent())
                : "";
        String price       = reg.getSession() != null && reg.getSession().getBootcamp() != null
                ? orDash(reg.getSession().getBootcamp().getPrice())
                : (reg.getBootcamp() != null ? orDash(reg.getBootcamp().getPrice()) : "—");

        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#333">
                
                  <!-- Header -->
                  <div style="background:linear-gradient(135deg,#1e3a5f 0%%,#0ea5e9 100%%);padding:28px 24px;border-radius:12px 12px 0 0;text-align:center">
                    <p style="color:rgba(255,255,255,.7);font-size:13px;margin:0 0 4px;text-transform:uppercase;letter-spacing:.05em">Model Technologie</p>
                    <h1 style="color:white;margin:0;font-size:24px;font-weight:700">Inscription reçue ✅</h1>
                    <p style="color:rgba(255,255,255,.85);margin:10px 0 0;font-size:15px">Merci %s, votre demande a bien été enregistrée !</p>
                  </div>
                
                  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:28px">
                
                    <!-- Récap formation -->
                    <div style="background:#f0f9ff;border:1px solid #bae6fd;border-radius:10px;padding:16px;margin-bottom:24px">
                      <p style="font-size:13px;font-weight:600;color:#0369a1;margin:0 0 10px;text-transform:uppercase;letter-spacing:.05em">📚 Votre formation</p>
                      <p style="font-size:18px;font-weight:700;color:#1e3a5f;margin:0 0 4px">%s</p>
                      <p style="font-size:14px;color:#64748b;margin:0 0 4px">Session : <strong>%s</strong></p>
                      <p style="font-size:14px;color:#64748b;margin:0 0 4px">Tarif : <strong>%s</strong></p>
                      %s
                      %s
                    </div>
                
                    <!-- Instructions paiement -->
                    <div style="background:#fff7ed;border:2px solid #fed7aa;border-radius:10px;padding:20px;margin-bottom:24px">
                      <h2 style="font-size:16px;font-weight:700;color:#c2410c;margin:0 0 12px">💳 Étape suivante : Effectuez votre paiement</h2>
                      <p style="font-size:14px;color:#374151;margin:0 0 14px;line-height:1.6">
                        Pour <strong>réserver définitivement votre place</strong>, veuillez effectuer le paiement des frais d'inscription dans les <strong>24 heures</strong>.
                      </p>
                
                      <!-- Moyens de paiement -->
                      <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:14px">
                        <div style="background:white;border:1px solid #e5e7eb;border-radius:8px;padding:12px 16px;flex:1;min-width:120px;text-align:center">
                          <p style="font-size:13px;font-weight:700;color:#1d4ed8;margin:0 0 4px">📱 Wave</p>
                          <p style="font-size:16px;font-weight:700;color:#111827;margin:0">%s</p>
                        </div>
                        <div style="background:white;border:1px solid #e5e7eb;border-radius:8px;padding:12px 16px;flex:1;min-width:120px;text-align:center">
                          <p style="font-size:13px;font-weight:700;color:#f59e0b;margin:0 0 4px">📱 Orange Money</p>
                          <p style="font-size:16px;font-weight:700;color:#111827;margin:0">%s</p>
                        </div>
                      </div>
                
                      <div style="background:#fef2f2;border:1px solid #fca5a5;border-radius:8px;padding:12px 14px;margin-bottom:14px">
                        <p style="font-size:14px;font-weight:600;color:#dc2626;margin:0">⏰ Délai : 24 heures</p>
                        <p style="font-size:13px;color:#374151;margin:4px 0 0;line-height:1.5">
                          Les places sont limitées et ne seront définitivement réservées qu'après réception du paiement.
                        </p>
                      </div>
                
                      <div style="background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:12px 14px">
                        <p style="font-size:14px;font-weight:600;color:#16a34a;margin:0 0 4px">📸 Envoyez votre preuve de paiement</p>
                        <p style="font-size:13px;color:#374151;margin:0;line-height:1.5">
                          Après paiement, envoyez une capture d'écran de votre transaction par <strong>WhatsApp</strong> au
                          <strong style="color:#1e3a5f"> +221 %s</strong>
                          en précisant votre nom et le bootcamp.
                        </p>
                      </div>
                    </div>
                
                    <!-- Contact -->
                    <div style="background:#f8fafc;border-radius:8px;padding:14px;margin-bottom:20px">
                      <p style="font-size:13px;color:#6b7280;margin:0 0 6px">❓ Des questions ?</p>
                      <p style="font-size:14px;color:#374151;margin:0">
                        Contactez-nous par WhatsApp au <strong>+221 %s</strong> ou répondez directement à cet email.
                      </p>
                    </div>
                
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:0 0 16px">
                    <p style="font-size:12px;color:#9ca3af;margin:0">Inscription enregistrée le %s · Model Technologie · Dakar, Sénégal</p>
                  </div>
                </body></html>
                """.formatted(
                e(fullName),              // salutation header
                e(bootcamp),              // nom formation
                e(session),               // session
                e(price),                 // tarif
                promoInfo,                // promo éventuelle
                placesInfo,               // places restantes
                paymentPhone,            // Wave
                paymentPhone,            // OM
                paymentPhone,            // WhatsApp preuve
                paymentPhone,            // contact
                date                      // footer
        );
    }

    /**
     * Email candidat — statut CONFIRMED.
     * Message de bienvenue chaleureux.
     */
    private String buildConfirmedCandidateHtml(Registration reg, String fullName, String bootcamp) {
        String session = orDash(reg.getSessionName());
        String date    = formatDate(reg);

        // Date de début depuis la session si disponible
        String startDate = "";
        if (reg.getSession() != null && reg.getSession().getStartDate() != null) {
            startDate = "<p style=\"font-size:14px;color:#64748b;margin:0 0 4px\">Début : <strong>"
                    + reg.getSession().getStartDate()
                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH))
                    + "</strong></p>";
        }

        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#333">
                
                  <!-- Header festif -->
                  <div style="background:linear-gradient(135deg,#065f46 0%%,#10b981 100%%);padding:32px 24px;border-radius:12px 12px 0 0;text-align:center">
                    <p style="font-size:40px;margin:0 0 8px">🎉</p>
                    <h1 style="color:white;margin:0;font-size:26px;font-weight:700">Félicitations %s !</h1>
                    <p style="color:rgba(255,255,255,.9);margin:10px 0 0;font-size:16px">Votre place est officiellement confirmée</p>
                  </div>
                
                  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:28px">
                
                    <!-- Message principal -->
                    <p style="font-size:15px;color:#374151;line-height:1.7;margin:0 0 20px">
                      Votre paiement a bien été reçu et validé. Vous faites désormais officiellement partie de la prochaine promotion <strong>%s</strong>.
                      Nous sommes ravis de vous accompagner dans ce parcours data !
                    </p>
                
                    <!-- Récap formation confirmée -->
                    <div style="background:#f0fdf4;border:2px solid #86efac;border-radius:10px;padding:18px;margin-bottom:24px">
                      <p style="font-size:13px;font-weight:600;color:#15803d;margin:0 0 10px;text-transform:uppercase;letter-spacing:.05em">✅ Votre inscription confirmée</p>
                      <p style="font-size:18px;font-weight:700;color:#14532d;margin:0 0 6px">%s</p>
                      <p style="font-size:14px;color:#64748b;margin:0 0 4px">Session : <strong>%s</strong></p>
                      %s
                    </div>
                
                    <!-- Message de motivation -->
                    <div style="background:#eff6ff;border-left:4px solid #3b82f6;border-radius:0 8px 8px 0;padding:16px;margin-bottom:24px">
                      <p style="font-size:15px;color:#1e40af;font-weight:600;margin:0 0 8px">🚀 Préparez-vous pour l'aventure data !</p>
                      <p style="font-size:14px;color:#374151;line-height:1.7;margin:0">
                        Les grandes carrières dans la data commencent par un premier pas courageux — vous venez de le franchir.
                        Notre équipe de formateurs est prête à vous transmettre des compétences qui feront la différence dans votre carrière.
                        Soyez prêt(e), motivé(e) et curieux(se) !
                      </p>
                    </div>
                
                    <!-- Prochaine étape -->
                    <div style="background:#f8fafc;border-radius:8px;padding:16px;margin-bottom:24px">
                      <p style="font-size:14px;font-weight:600;color:#374151;margin:0 0 8px">📌 Prochaines étapes</p>
                      <ul style="font-size:14px;color:#374151;line-height:1.8;margin:0;padding-left:18px">
                        <li>Vous recevrez les détails pratiques (lieu, horaires, matériel) quelques jours avant le début</li>
                        <li>Rejoignez notre groupe WhatsApp via le <strong>+221 %s</strong></li>
                        <li>En cas de question, contactez-nous par WhatsApp ou répondez à cet email</li>
                      </ul>
                    </div>
                
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:0 0 16px">
                    <p style="font-size:12px;color:#9ca3af;margin:0">Confirmation émise le %s · Model Technologie · Dakar, Sénégal</p>
                  </div>
                </body></html>
                """.formatted(
                e(fullName),    // félicitations header
                e(bootcamp),    // promotion
                e(bootcamp),    // recap
                e(session),     // session
                startDate,      // date de début éventuelle
                paymentPhone,  // groupe WhatsApp
                date            // footer
        );
    }

    /** Email interne équipe — message de contact */
    private String buildContactHtml(ContactMessage contact, String fullName, String sujet) {
        String date       = contact.getCreatedAt() != null
                ? contact.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")) : "—";
        String entreprise = orDash(contact.getCompany());

        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#333">
                  <div style="background:linear-gradient(135deg,#1e3a5f 0%%,#0ea5e9 100%%);padding:24px;border-radius:12px 12px 0 0">
                    <h1 style="color:white;margin:0;font-size:20px">✉️ Nouveau message de contact</h1>
                    <p style="color:rgba(255,255,255,.8);margin:8px 0 0">Model Technologie — Data Mastery Hub</p>
                  </div>
                  <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:24px">
                    <table style="width:100%%;border-collapse:collapse">
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280;width:140px">Nom</td><td style="padding:8px 0">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Email</td><td style="padding:8px 0"><a href="mailto:%s" style="color:#0ea5e9">%s</a></td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Sujet</td><td style="padding:8px 0;font-weight:600;color:#1e3a5f">%s</td></tr>
                      <tr><td style="padding:8px 0;font-weight:600;color:#6b7280">Entreprise</td><td style="padding:8px 0">%s</td></tr>
                    </table>
                    <div style="margin-top:16px;padding:16px;background:#f9fafb;border-left:4px solid #0ea5e9;border-radius:0 8px 8px 0">
                      <p style="font-weight:600;color:#6b7280;margin:0 0 8px">Message :</p>
                      <p style="margin:0;white-space:pre-wrap;line-height:1.6">%s</p>
                    </div>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:16px 0">
                    <p style="font-size:13px;color:#9ca3af;margin:0">Reçu le %s · 💡 Répondez à cet email pour contacter <strong>%s</strong></p>
                  </div>
                </body></html>
                """.formatted(
                e(fullName),
                e(contact.getEmail()), e(contact.getEmail()),
                e(sujet),
                e(entreprise),
                e(contact.getMessage()),
                date, e(fullName)
        );
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    /**
     * Construit le bloc HTML "places restantes" pour l'email pending.
     * N'affiche rien si on n'a pas accès aux données de session.
     */
    private String buildPlacesInfo(Registration reg) {
        if (reg.getSession() == null) return "";
        Integer max     = reg.getSession().getMaxParticipants();
        Integer current = reg.getSession().getCurrentParticipants();
        if (max == null || current == null || max <= 0) return "";

        int remaining = Math.max(0, max - current);
        String color  = remaining <= 3 ? "#dc2626" : (remaining <= 7 ? "#d97706" : "#16a34a");

        return "<p style=\"font-size:14px;color:%s;font-weight:600;margin:4px 0 0\">🪑 %d place%s restante%s sur %d</p>"
                .formatted(color, remaining, remaining > 1 ? "s" : "", remaining > 1 ? "s" : "", max);
    }

    private String promoRow(Registration reg) {
        if (reg.getPromoCodeUsed() == null) return "";
        return "<tr><td style=\"padding:8px 0;font-weight:600;color:#6b7280\">Code promo</td>"
                + "<td style=\"padding:8px 0;color:#16a34a;font-weight:600\">"
                + e(reg.getPromoCodeUsed()) + " (-" + reg.getDiscountPercent() + "%)</td></tr>";
    }

    private String messageRow(Registration reg) {
        if (reg.getMessage() == null || reg.getMessage().isBlank()) return "";
        return "<tr><td style=\"padding:8px 0;font-weight:600;color:#6b7280;vertical-align:top\">Message</td>"
                + "<td style=\"padding:12px;background:#f9fafb;border-radius:8px\">"
                + e(reg.getMessage()) + "</td></tr>";
    }

    private String formatDate(Registration reg) {
        return reg.getCreatedAt() != null
                ? reg.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"))
                : "—";
    }

    private String fullName(Registration reg) {
        return reg.getFirstName() + " " + reg.getLastName();
    }

    private String orDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private boolean isDisabled() {
        if (internalEmail == null || internalEmail.isBlank()) {
            log.debug("Email destinataire interne non configuré, notification ignorée");
            return true;
        }
        return false;
    }

    /** Échappe les caractères HTML sensibles */
    private String e(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
