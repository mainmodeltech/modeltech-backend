package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.auth.service.PasswordlessLoginService;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimOrder;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Observer de {@link OrderPaid} : une fois l'accès ouvert et la transaction validée, envoie la confirmation d'achat et un lien de
 * connexion. Une panne d'e-mail ne remet jamais en cause un paiement confirmé : l'accès est déjà ouvert, l'apprenant peut se
 * connecter depuis la page de connexion (« recevoir un lien »).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotifier {

    private final SimOrderRepository orders;
    private final MessageDispatcher dispatcher;
    private final PasswordlessLoginService passwordless;

    @Value("${app.frontend.url:http://localhost:8088}")
    private String frontendUrl;

    /**
     * Après validation, la transaction de l'achat est terminée : tout ce qui écrit en base (le lien de connexion) doit ouvrir la sienne
     * ({@code REQUIRES_NEW}, en écriture : un « lecture seule » ferait échouer cette écriture ; sans transaction du tout, Spring réutiliserait
     * celle, close, de l'achat : « no transaction is in progress »).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaid(OrderPaid event) {
        try {
            SimOrder o = orders.findDetailedById(event.orderId()).orElseThrow();
            send(o);
        } catch (RuntimeException e) {
            log.error("Confirmation d'achat non envoyée pour la commande {} : {}", event.orderId(), e.getMessage());
        }
    }

    void send(SimOrder o) {
        String email = o.getLearner().getEmail();
        String name = o.getLearner().getFirstName();
        String amount = NumberFormat.getIntegerInstance(Locale.FRANCE).format(o.getChargedXof()).replace(' ', ' ').replace(' ', ' ') + " FCFA";
        LocalDateTime until = o.getPaidAt().plusDays(o.getDurationDays());
        EmailContent.Builder b = EmailContent.builder("Votre accès au simulateur PL-300 est ouvert")
                .preheader("Paiement confirmé : " + amount)
                .greeting(name != null && !name.isBlank() ? "Bonjour " + name.trim() + "," : "Bonjour,")
                .paragraph("Merci pour votre achat : votre paiement est **confirmé** et votre accès au simulateur est **ouvert**.")
                .details(List.of(
                        new EmailContent.Entry("Forfait", o.getPlanName()),
                        new EmailContent.Entry("Montant", amount),
                        new EmailContent.Entry("Examens blancs", String.valueOf(o.getExamAttempts())),
                        new EmailContent.Entry("Accès jusqu'au", until.format(DateTimeFormatter.ofPattern("d/MM/yyyy")))))
                .paragraph("Pour vous connecter, utilisez le lien de connexion que vous recevez dans un second e-mail (ou demandez-en un depuis la page de connexion).")
                .button("Ouvrir le simulateur", frontendUrl + "/espace/simulateur");
        if (o.getReceiptUrl() != null) b.button("Voir mon reçu de paiement", o.getReceiptUrl());
        EmailContent rich = b.footnote("Une question ? Répondez à ce message ou écrivez-nous depuis la page Contact.").build();
        dispatcher.sendAll(List.of(new OutboundMessage("SIM_ORDER_PAID", email, null, name, "Votre accès au simulateur PL-300 est ouvert",
                "Paiement confirmé : forfait " + o.getPlanName() + ", " + amount + ". Accès jusqu'au " + until.toLocalDate() + ".", null).withEmail(rich)), null);
        passwordless.request(email);
    }
}
