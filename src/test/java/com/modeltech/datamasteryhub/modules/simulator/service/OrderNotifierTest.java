package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.service.PasswordlessLoginService;
import com.modeltech.datamasteryhub.modules.notification.channel.MessageDispatcher;
import com.modeltech.datamasteryhub.modules.notification.channel.OutboundMessage;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimOrder;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Confirmation d'achat : e-mail + lien de connexion, sans jamais dépendre d'une transaction « lecture seule » (sans Spring). */
class OrderNotifierTest {

    private SimOrderRepository orders;
    private MessageDispatcher dispatcher;
    private PasswordlessLoginService passwordless;
    private OrderNotifier notifier;
    private SimOrder order;

    @BeforeEach
    void setUp() {
        orders = mock(SimOrderRepository.class);
        dispatcher = mock(MessageDispatcher.class);
        passwordless = mock(PasswordlessLoginService.class);
        notifier = new OrderNotifier(orders, dispatcher, passwordless);
        ReflectionTestUtils.setField(notifier, "frontendUrl", "https://site.test");

        Learner learner = new Learner();
        learner.setEmail("awa@test.local");
        learner.setFirstName("Awa");
        learner.setLastName("Diop");
        order = new SimOrder();
        order.setLearner(learner);
        order.setPlanCode(PlanCode.PRO);
        order.setPlanName("Pro");
        order.setAmountXof(45000);
        order.setDurationDays(90);
        order.setExamAttempts(5);
        order.setPaidAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        order.setReceiptUrl("https://receipt.test/r.pdf");
    }

    @Test
    void aPaidOrderSendsTheConfirmationAndALoginLink() {
        UUID id = UUID.randomUUID();
        when(orders.findDetailedById(id)).thenReturn(Optional.of(order));
        notifier.onPaid(new OrderPaid(id));

        @SuppressWarnings("unchecked") ArgumentCaptor<List<OutboundMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).sendAll(sent.capture(), eq(null));
        OutboundMessage m = sent.getValue().get(0);
        assertThat(m.type()).isEqualTo("SIM_ORDER_PAID");
        assertThat(m.toEmail()).isEqualTo("awa@test.local");
        assertThat(m.body()).contains("Pro").contains("45 000 FCFA").contains("2027-01-31");
        verify(passwordless).request("awa@test.local");
    }

    @Test
    void aMailFailureNeverBreaksAPaymentThatIsAlreadyConfirmed() {
        UUID id = UUID.randomUUID();
        when(orders.findDetailedById(id)).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("smtp down")).when(dispatcher).sendAll(any(), any());
        notifier.onPaid(new OrderPaid(id));            // ne lève rien
        when(orders.findDetailedById(id)).thenReturn(Optional.empty());
        notifier.onPaid(new OrderPaid(id));            // commande introuvable : journalisé, pas d'exception
    }

    @Test
    void theListenerRunsInItsOwnWritableTransaction_becauseTheLoginLinkWritesAfterThePurchaseTransactionHasEnded() throws Exception {
        // Régression : « readOnly » faisait échouer l'écriture du lien ; sans transaction propre, Spring réutilisait celle (close) de l'achat.
        Method m = OrderNotifier.class.getMethod("onPaid", OrderPaid.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
        assertThat(tx.readOnly()).isFalse();
    }
}
