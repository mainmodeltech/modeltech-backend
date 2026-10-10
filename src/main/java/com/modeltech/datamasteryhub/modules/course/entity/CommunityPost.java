package com.modeltech.datamasteryhub.modules.course.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Message du fil d'entraide d'une formation, ou réponse (un seul niveau) à un message. */
@Entity
@Table(name = "community_posts")
@Getter @Setter @NoArgsConstructor
public class CommunityPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bootcamp_id", nullable = false, updatable = false)
    private Bootcamp bootcamp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false, updatable = false)
    private Learner learner;

    /** Message auquel on répond ; nul pour un message de premier niveau. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private CommunityPost parent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
