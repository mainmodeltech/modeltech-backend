package com.modeltech.datamasteryhub.modules.notification;

import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.NotificationChannel;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Les canaux sont interchangeables : ajouter WhatsApp ne demande qu'un nouveau canal activé par configuration. */
class MessageDispatcherTest {

    /** Canal de test : enregistre ce qu'il envoie. */
    private static final class FakeChannel implements NotificationChannel {
        private final String name;
        private final boolean needsPhone;
        private final boolean fails;
        final List<OutboundMessage> sent = new ArrayList<>();

        FakeChannel(String name, boolean needsPhone, boolean fails) {
            this.name = name;
            this.needsPhone = needsPhone;
            this.fails = fails;
        }

        @Override public String name() { return name; }

        @Override public boolean canReach(OutboundMessage m) {
            return needsPhone ? m.toPhone() != null : m.toEmail() != null;
        }

        @Override public boolean send(OutboundMessage m) {
            if (fails) throw new IllegalStateException("panne du canal");
            sent.add(m);
            return true;
        }
    }

    private final OutboundMessage toBoth = new OutboundMessage("TEST", "awa@example.com", "221770000000", "Awa", "Sujet", "Corps", null);

    @Test
    void onlyEnabledChannelsAreUsed_andOnlyWhenTheyCanReachTheRecipient() {
        FakeChannel email = new FakeChannel("EMAIL", false, false);
        FakeChannel whatsapp = new FakeChannel("WHATSAPP", true, false);

        assertThat(new MessageDispatcher(List.of(email, whatsapp), "EMAIL").send(toBoth)).isTrue();
        assertThat(email.sent).hasSize(1);
        assertThat(whatsapp.sent).isEmpty();                       // WhatsApp existe mais n'est pas activé

        // Activer WhatsApp = une ligne de configuration
        assertThat(new MessageDispatcher(List.of(email, whatsapp), "email, whatsapp").send(toBoth)).isTrue();
        assertThat(email.sent).hasSize(2);
        assertThat(whatsapp.sent).hasSize(1);

        // Sans numéro, WhatsApp est ignoré sans erreur
        OutboundMessage noPhone = OutboundMessage.of("TEST", "awa@example.com", "Awa", "S", "C");
        new MessageDispatcher(List.of(email, whatsapp), "EMAIL,WHATSAPP").send(noPhone);
        assertThat(whatsapp.sent).hasSize(1);
    }

    @Test
    void aFailingChannelNeverBlocksTheOthers_orThrows() {
        FakeChannel broken = new FakeChannel("WHATSAPP", true, true);
        FakeChannel email = new FakeChannel("EMAIL", false, false);

        boolean delivered = new MessageDispatcher(List.of(broken, email), "WHATSAPP,EMAIL").send(toBoth);

        assertThat(delivered).isTrue();                            // l'e-mail est parti malgré la panne de l'autre canal
        assertThat(email.sent).hasSize(1);
        assertThat(new MessageDispatcher(List.of(broken), "WHATSAPP").send(toBoth)).isFalse();
    }

    @Test
    void sendAll_countsDeliveredMessages_andReportsThem() {
        FakeChannel email = new FakeChannel("EMAIL", false, false);
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(email), "EMAIL");
        AtomicInteger reported = new AtomicInteger(-1);

        dispatcher.sendAll(List.of(toBoth, OutboundMessage.of("TEST", "b@example.com", "B", "S", "C"),
                new OutboundMessage("TEST", null, null, "Sans adresse", "S", "C", null)), reported::set);

        assertThat(email.sent).hasSize(2);
        assertThat(reported.get()).isEqualTo(2);                   // le destinataire sans adresse n'est pas compté
    }
}
