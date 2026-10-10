package com.modeltech.datamasteryhub.modules.course.service;

import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.auth.entity.Learner;
import com.modeltech.datamasteryhub.modules.course.dto.CommunityPayloads;
import com.modeltech.datamasteryhub.modules.course.entity.CommunityPost;
import com.modeltech.datamasteryhub.modules.course.repository.CommunityPostRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fil d'entraide par formation : les apprenants ayant accès à la formation écrivent et répondent (un seul niveau) ;
 * un auteur supprime ses messages, l'équipe modère. Les e-mails ne sont jamais montrés aux autres apprenants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityService {

    private static final int MAX_PAGE_SIZE = 50;

    private final LearnerAccess access;
    private final CommunityPostRepository postRepository;
    private final BootcampRepository bootcampRepository;

    // ── Apprenants ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CommunityPayloads.ThreadPage threads(String learnerEmail, UUID formationId, int page, int size) {
        Learner learner = access.requireLearner(learnerEmail);
        access.requireAccess(learner, formationId);

        Page<CommunityPost> roots = postRepository.findAllByBootcampIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
                formationId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        Map<UUID, List<CommunityPost>> replies = roots.isEmpty() ? Map.of()
                : postRepository.findAllByParentIdInAndIsDeletedFalseOrderByCreatedAtAsc(roots.map(CommunityPost::getId).toList())
                        .stream().collect(Collectors.groupingBy(r -> r.getParent().getId()));

        return CommunityPayloads.ThreadPage.builder()
                .threads(roots.stream().map(root -> toPost(root, learner, replies.getOrDefault(root.getId(), List.of()))).toList())
                .totalThreads(roots.getTotalElements())
                .page(roots.getNumber())
                .totalPages(roots.getTotalPages())
                .build();
    }

    @Transactional
    public CommunityPayloads.Post post(String learnerEmail, UUID formationId, CommunityPayloads.CreateRequest request) {
        Learner learner = access.requireLearner(learnerEmail);
        access.requireAccess(learner, formationId);
        Bootcamp formation = bootcampRepository.findByIdAndIsDeletedFalse(formationId)
                .orElseThrow(() -> new ResourceNotFoundException("Formation", "id", formationId));

        CommunityPost parent = null;
        if (request.getParentId() != null) {
            parent = postRepository.findByIdAndIsDeletedFalse(request.getParentId())
                    .filter(p -> p.getBootcamp().getId().equals(formationId) && p.getParent() == null)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "On ne peut répondre qu'à un message de cette formation."));
        }

        CommunityPost post = new CommunityPost();
        post.setBootcamp(formation);
        post.setLearner(learner);
        post.setParent(parent);
        post.setContent(request.getContent().trim());
        return toPost(postRepository.save(post), learner, List.of());
    }

    /** Un apprenant supprime SON message ; les réponses qui lui étaient liées disparaissent avec lui. */
    @Transactional
    public void deleteOwn(String learnerEmail, UUID postId) {
        Learner learner = access.requireLearner(learnerEmail);
        CommunityPost post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", "id", postId));
        if (!post.getLearner().getId().equals(learner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous ne pouvez supprimer que vos propres messages.");
        }
        softDelete(post, learnerEmail);
    }

    // ── Modération (équipe) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommunityPayloads.ModerationPost> moderationList(UUID formationId, int page, int size) {
        Page<CommunityPost> roots = postRepository.findAllByBootcampIdAndParentIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
                formationId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<CommunityPost> replies = roots.isEmpty() ? List.of()
                : postRepository.findAllByParentIdInAndIsDeletedFalseOrderByCreatedAtAsc(roots.map(CommunityPost::getId).toList());
        Map<UUID, List<CommunityPost>> byParent = replies.stream().collect(Collectors.groupingBy(r -> r.getParent().getId()));
        return roots.stream()
                .flatMap(root -> java.util.stream.Stream.concat(java.util.stream.Stream.of(root), byParent.getOrDefault(root.getId(), List.of()).stream()))
                .map(CommunityService::toModeration)
                .toList();
    }

    @Transactional
    public void moderate(UUID postId, String actor) {
        CommunityPost post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", "id", postId));
        softDelete(post, actor);
        log.info("Message {} de la communauté supprimé par {}", postId, actor);
    }

    // ── Interne ──────────────────────────────────────────────────────

    private void softDelete(CommunityPost post, String actor) {
        for (CommunityPost reply : postRepository.findAllByParentIdAndIsDeletedFalse(post.getId())) {
            reply.setDeleted(true);
            reply.setDeletedBy(actor);
            reply.setDeletedAt(java.time.LocalDateTime.now());
            postRepository.save(reply);
        }
        post.setDeleted(true);
        post.setDeletedBy(actor);
        post.setDeletedAt(java.time.LocalDateTime.now());
        postRepository.save(post);
    }

    private static CommunityPayloads.Post toPost(CommunityPost post, Learner viewer, List<CommunityPost> replies) {
        return CommunityPayloads.Post.builder()
                .id(post.getId())
                .author(publicName(post.getLearner()))
                .mine(post.getLearner().getId().equals(viewer.getId()))
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .replies(replies.stream().map(r -> toPost(r, viewer, List.of())).toList())
                .build();
    }

    private static CommunityPayloads.ModerationPost toModeration(CommunityPost post) {
        return CommunityPayloads.ModerationPost.builder()
                .id(post.getId())
                .parentId(post.getParent() != null ? post.getParent().getId() : null)
                .authorName(post.getLearner().getFullName())
                .authorEmail(post.getLearner().getEmail())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .build();
    }

    /** « Awa D. » : prénom et initiale du nom. */
    static String publicName(Learner learner) {
        String first = capitalize(learner.getFirstName() == null ? "" : learner.getFirstName().trim());
        String last = learner.getLastName() == null ? "" : learner.getLastName().trim();
        return last.isEmpty() ? first : first + " " + Character.toUpperCase(last.charAt(0)) + ".";
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
