package com.modeltech.datamasteryhub.modules.simulator.service;

import com.modeltech.datamasteryhub.modules.simulator.entity.ItemStatus;
import com.modeltech.datamasteryhub.modules.simulator.entity.SimItemVersion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cycle de vie d'une version de question et séparation des tâches (sans Spring). */
class ItemWorkflowTest {

    private static final String AUTHOR = "auteur@test.local";
    private static final String OTHER = "relecteur@test.local";
    private static final String EXPLANATION = "Une explication en français suffisamment longue.";

    private static SimItemVersion draft() {
        SimItemVersion v = new SimItemVersion();
        v.setAuthor(AUTHOR);
        v.setStatus(ItemStatus.DRAFT);
        v.setExplanationFr(EXPLANATION);
        return v;
    }

    private static void status(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    @Test
    void happyPath_draftToRetired() {
        SimItemVersion v = draft();
        ItemWorkflow.submit(v, AUTHOR);
        assertThat(v.getStatus()).isEqualTo(ItemStatus.IN_REVIEW);
        ItemWorkflow.approve(v, OTHER, true, "RAS");
        assertThat(v.getStatus()).isEqualTo(ItemStatus.APPROVED);
        assertThat(v.getVerifiedBy()).isEqualTo(OTHER);
        ItemWorkflow.publish(v, OTHER);
        assertThat(v.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
        assertThat(v.getPublisher()).isEqualTo(OTHER);
        ItemWorkflow.retire(v, "Obsolète");
        assertThat(v.getStatus()).isEqualTo(ItemStatus.RETIRED);
    }

    @Test
    void theAuthorNeverReviewsNorPublishesOwnQuestion_caseInsensitive() {
        SimItemVersion v = draft();
        ItemWorkflow.submit(v, AUTHOR);
        status(() -> ItemWorkflow.approve(v, AUTHOR.toUpperCase(), true, null), HttpStatus.FORBIDDEN);
        status(() -> ItemWorkflow.requestChanges(v, AUTHOR, "x"), HttpStatus.FORBIDDEN);

        ItemWorkflow.approve(v, OTHER, true, null);
        status(() -> ItemWorkflow.publish(v, AUTHOR), HttpStatus.FORBIDDEN);
    }

    @Test
    void approvalNeedsTheTechnicalVerificationAttestation() {
        SimItemVersion v = draft();
        ItemWorkflow.submit(v, AUTHOR);
        status(() -> ItemWorkflow.approve(v, OTHER, false, null), HttpStatus.BAD_REQUEST);
        assertThat(v.getStatus()).isEqualTo(ItemStatus.IN_REVIEW);
    }

    @Test
    void submitNeedsAFrenchExplanation_andOnlyTheAuthorEditsADraft() {
        SimItemVersion noExplanation = draft();
        noExplanation.setExplanationFr("court");
        status(() -> ItemWorkflow.submit(noExplanation, AUTHOR), HttpStatus.BAD_REQUEST);
        status(() -> ItemWorkflow.requireEditable(draft(), OTHER), HttpStatus.FORBIDDEN);

        SimItemVersion inReview = draft();
        ItemWorkflow.submit(inReview, AUTHOR);
        status(() -> ItemWorkflow.requireEditable(inReview, AUTHOR), HttpStatus.CONFLICT);
    }

    @Test
    void requestChangesSendsTheQuestionBackToDraftWithANote() {
        SimItemVersion v = draft();
        ItemWorkflow.submit(v, AUTHOR);
        status(() -> ItemWorkflow.requestChanges(v, OTHER, " "), HttpStatus.BAD_REQUEST);
        ItemWorkflow.requestChanges(v, OTHER, "La bonne réponse est discutable");
        assertThat(v.getStatus()).isEqualTo(ItemStatus.DRAFT);
        assertThat(v.getReviewNote()).contains("discutable");
    }

    @Test
    void forbiddenTransitionsAreRefused() {
        SimItemVersion v = draft();
        status(() -> ItemWorkflow.publish(v, OTHER), HttpStatus.CONFLICT);          // brouillon non approuvé
        status(() -> ItemWorkflow.approve(v, OTHER, true, null), HttpStatus.CONFLICT); // pas en relecture
        status(() -> ItemWorkflow.retire(v, "x"), HttpStatus.CONFLICT);             // pas publiée
        status(() -> ItemWorkflow.submit(v, OTHER), HttpStatus.FORBIDDEN);

        ItemWorkflow.submit(v, AUTHOR);
        ItemWorkflow.approve(v, OTHER, true, null);
        ItemWorkflow.publish(v, OTHER);
        status(() -> ItemWorkflow.retire(v, " "), HttpStatus.BAD_REQUEST);
    }
}
