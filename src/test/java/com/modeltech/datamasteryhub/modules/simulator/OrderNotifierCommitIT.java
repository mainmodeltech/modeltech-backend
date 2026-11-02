package com.modeltech.datamasteryhub.modules.simulator;

import com.modeltech.datamasteryhub.AbstractIntegrationTest;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.auth.repository.LearnerRepository;
import com.modeltech.datamasteryhub.modules.simulator.entity.OrderStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.PlanCode;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimOrder;
import com.modeltech.datamasteryhub.modules.simulator.repository.SimOrderRepository;
import com.modeltech.datamasteryhub.modules.simulator.service.OrderPaid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le seul test où la transaction est VRAIMENT validée : les écouteurs « après validation » ne se déclenchent pas dans les tests annulés.
 * Il garde le piège découvert à la main : écrire le lien de connexion depuis cet écouteur exige une transaction indépendante.
 * Les lignes créées sont supprimées à la fin (aucune trace dans le journal d'audit : on ne publie que l'événement).
 */
class OrderNotifierCommitIT extends AbstractIntegrationTest {

    @Autowired private LearnerRepository learners;
    @Autowired private SimOrderRepository orders;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void afterTheCommit_theBuyerGetsTheConfirmationAndALoginLink() throws Exception {
        String email = "commit.it@test.local";
        Learner l = new Learner();
        l.setEmail(email);
        l.setFirstName("Awa");
        l.setLastName("Diop");
        learners.save(l);
        SimOrder o = new SimOrder();
        o.setLearner(l);
        o.setPlanCode(PlanCode.PRO);
        o.setPlanName("Pro");
        o.setAmountXof(45000);
        o.setDurationDays(90);
        o.setExamAttempts(5);
        o.setProvider("PAYDUNYA");
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(LocalDateTime.now());
        orders.save(o);
        events.publishEvent(new OrderPaid(o.getId()));

        TestTransaction.flagForCommit();
        TestTransaction.end();            // validation réelle : l'écouteur s'exécute ici
        TestTransaction.start();
        try {
            // le lien de connexion s'écrit dans l'écouteur (synchrone) ; l'envoi de l'e-mail est asynchrone
            assertThat(jdbc.queryForObject("SELECT count(*) FROM login_challenges WHERE email = ?", Integer.class, email)).isEqualTo(1);
            int logged = 0;
            for (int i = 0; i < 30 && logged == 0; i++) {
                logged = jdbc.queryForObject("SELECT count(*) FROM email_logs WHERE recipient = ? AND subject LIKE 'Votre accès au simulateur%'", Integer.class, email);
                if (logged == 0) Thread.sleep(200);
            }
            assertThat(logged).isGreaterThanOrEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM login_challenges WHERE email = ?", email);
            jdbc.update("DELETE FROM email_logs WHERE recipient = ?", email);
            jdbc.update("DELETE FROM sim_order WHERE learner_id = (SELECT id FROM learners WHERE email = ?)", email);
            jdbc.update("DELETE FROM learner_roles WHERE learner_id = (SELECT id FROM learners WHERE email = ?)", email);
            jdbc.update("DELETE FROM learners WHERE email = ?", email);
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }
}
