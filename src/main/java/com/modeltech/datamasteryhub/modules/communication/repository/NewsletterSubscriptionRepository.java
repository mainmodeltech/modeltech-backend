package com.modeltech.datamasteryhub.modules.communication.repository;

import com.modeltech.datamasteryhub.common.persistence.SoftDeleteRepository;
import com.modeltech.datamasteryhub.modules.communication.entity.NewsletterSubscription;
import com.modeltech.datamasteryhub.modules.communication.enums.NewsletterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsletterSubscriptionRepository extends SoftDeleteRepository<NewsletterSubscription, UUID> {

    Optional<NewsletterSubscription> findByEmail(String email);

    Optional<NewsletterSubscription> findByConfirmationToken(String token);

    Optional<NewsletterSubscription> findByUnsubscribeToken(String token);

    Page<NewsletterSubscription> findAllByStatusAndIsDeletedFalse(NewsletterStatus status, Pageable pageable);
}
