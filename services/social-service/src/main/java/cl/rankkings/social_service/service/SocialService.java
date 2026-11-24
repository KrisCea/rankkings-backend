package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.dto.SocialDtos.NewCommentInput;
import cl.rankkings.social_service.dto.SocialDtos.NewPostInput;
import cl.rankkings.social_service.dto.SocialDtos.NewReviewInput;
import cl.rankkings.social_service.dto.SocialDtos.RatingInput;
import cl.rankkings.social_service.dto.SocialDtos.ShareInput;
import cl.rankkings.social_service.model.CommentLike;
import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.PostRating;
import cl.rankkings.social_service.model.PostShare;
import cl.rankkings.social_service.model.PostVote;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.LikeRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import cl.rankkings.social_service.repository.ShareRepository;
import cl.rankkings.social_service.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialService {
    private final PostRepository posts;
    private final VoteRepository votes;
    private final RatingRepository ratings;
    private final CommentRepository comments;
    private final LikeRepository likes;
    private final ShareRepository shares;
    private final CatalogServiceClient catalogClient;
    private final IdentityServiceClient identityClient;
    private final PostMediaService postMediaService;
    private final ModerationService moderationService;
    @Value("${rankkings.internal-key:}") private String internalKey;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> feed(String viewerId) {
        return posts.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> canView(post, viewerId))
                .limit(100).map(post -> present(post, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> featured(String viewerId) {
        return posts.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> canView(post, viewerId))
                .limit(12).map(post -> present(post, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPost(String id, String viewerId) {
        SocialPost post = requirePost(id);
        requireVisible(post, viewerId);
        return present(post, viewerId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getItemPost(String itemId, String viewerId) {
        SocialPost post = posts.findFirstByItemIdAndKindOrderByCreatedAtAsc(itemId, "item")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La obra aún no tiene publicación"));
        requireVisible(post, viewerId);
        return present(post, viewerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> userPosts(String username, String kind, String viewerId) {
        validateKind(kind);
        JsonNode user = identityClient.findUserByUsername(username);
        if ("review".equals(kind)) {
            return comments.findByAuthorIdAndKindAndDeletedAtIsNullOrderByCreatedAtDesc(key(user, "id"), "review")
                    .stream().filter(comment -> canView(comment, viewerId))
                    .limit(100).map(comment -> presentComment(comment, viewerId)).toList();
        }
        return posts.findByAuthorIdAndKindOrderByCreatedAtDesc(key(user, "id"), kind)
                .stream().filter(post -> canView(post, viewerId))
                .limit(100).map(post -> present(post, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> itemReviews(String itemId, String viewerId) {
        catalogClient.getItem(itemId, null);
        return posts.findByItemIdAndKindOrderByCreatedAtDesc(itemId, "item").stream()
                .findFirst()
                .filter(post -> canView(post, viewerId))
                .map(post -> comments.findByPostIdAndKindAndDeletedAtIsNullOrderByCreatedAtDesc(post.getId(), "review")
                        .stream().filter(comment -> canView(comment, viewerId))
                        .map(comment -> presentComment(comment, viewerId)).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> moderationQueue(
            String status,
            String type,
            int limit,
            String cursor,
            String viewerId
    ) {
        return moderationService.queue(status, type, limit, cursor, viewerId);
    }

    @Transactional
    public Map<String, Object> decideModeration(
            String type,
            String id,
            boolean approve,
            String reason,
            String viewerId
    ) {
        return moderationService.decide(type, id, approve, reason, viewerId);
    }

    @Transactional
    public Map<String, Object> createPost(NewPostInput input, String viewerId) {
        String authorId = requireUser(viewerId);
        validateKind(input.kind());
        if ("review".equals(input.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Crea la reseña como comentario del post del ítem");
        }
        boolean creatingItem = input.rankableItem() != null;
        if (creatingItem == (input.itemId() != null && !input.itemId().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Indica un itemId existente o los datos de rankableItem, pero no ambos");
        }
        if (creatingItem
                && input.rankableItem().creator() == null
                && (input.rankableItem().creatorName() == null || input.rankableItem().creatorName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica creatorName o creator.name");
        }

        String postId = UUID.randomUUID().toString();
        if (creatingItem) {
            registerItemCompensation(postId);
        }
        boolean uploaderVerified = creatingItem
                && identityClient.getUser(authorId).path("isVerified").asBoolean(false);
        JsonNode item = creatingItem
                ? catalogClient.createItem(input.rankableItem(), authorId, postId, uploaderVerified)
                : catalogClient.getItem(input.itemId(), null);
        String itemId = key(item, "id");
        if (itemId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El catálogo no devolvió el ID del Rankable Item");
        }
        if ("item".equals(input.kind()) && posts.existsByItemIdAndKind(itemId, "item")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este ítem ya tiene publicación");
        }

        String catalogPostId = key(item, "postId");
        if (!creatingItem && "item".equals(input.kind()) && !catalogPostId.isBlank()) {
            postId = catalogPostId;
        }
        if (posts.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ID de publicación ocupado");
        }

        SocialPost post = new SocialPost();
        post.setId(postId);
        post.setAuthorId(authorId);
        post.setItemId(itemId);
        post.setKind(input.kind());
        post.setCaption(input.caption() == null ? "" : input.caption().trim());
        post.setModerationStatus("approved");
        posts.saveAndFlush(post);
        return present(post, viewerId);
    }

    @Transactional
    public void deletePost(String postId, String viewerId) {
        requireUser(viewerId);
        SocialPost post = posts.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
        requireOwnerOrAdmin(post.getAuthorId(), viewerId);

        List<PostComment> postComments = comments.findByPostIdOrderByCreatedAtAsc(postId);
        List<String> commentIds = postComments.stream().map(PostComment::getId).toList();
        if (!commentIds.isEmpty()) {
            likes.deleteAll(likes.findByCommentIdIn(commentIds));
        }
        comments.deleteAll(postComments);
        ratings.deleteAll(ratings.findByPostId(postId));
        votes.deleteAll(votes.findByPostId(postId));
        shares.deleteAll(shares.findByPostId(postId));
        postMediaService.deleteForPost(postId);
        posts.delete(post);

        if ("item".equals(post.getKind())) {
            afterCommit(() -> catalogClient.deleteCreatedItem(postId), "Rankable Item", postId);
        }
    }

    @Transactional
    public void deleteReview(String postId, String reviewId, String viewerId) {
        requireUser(viewerId);
        SocialPost post = requirePost(postId);
        requireItemPost(post);
        PostComment review = comments.findById(reviewId)
                .filter(comment -> postId.equals(comment.getPostId()) && "review".equals(comment.getKind()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
        requireOwnerOrAdmin(review.getAuthorId(), viewerId);

        if (review.getDeletedAt() == null) {
            review.setDeletedAt(Instant.now());
            comments.save(review);
            likes.deleteAll(likes.findByCommentId(reviewId));
            if (review.getAuthorScore() != null) {
                Optional<PostRating> rating = ratings.findByPostIdAndUserId(postId, review.getAuthorId());
                Optional<PostComment> replacementReview =
                        comments.findFirstByPostIdAndAuthorIdAndKindAndModerationStatusAndDeletedAtIsNullAndAuthorScoreIsNotNullOrderByCreatedAtDesc(
                                postId, review.getAuthorId(), "review", "approved");
                if (replacementReview.isPresent()) {
                    rating.ifPresent(existing -> {
                        existing.setScore(replacementReview.get().getAuthorScore());
                        ratings.save(existing);
                    });
                } else {
                    rating.ifPresent(ratings::delete);
                }
                syncCatalogRatings(post);
            }
        }
    }

    @Transactional
    public void deleteComment(String commentId, String viewerId) {
        requireUser(viewerId);
        PostComment comment = comments.findById(commentId)
                .filter(value -> "comment".equals(value.getKind()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario no encontrado"));
        requireOwnerOrAdmin(comment.getAuthorId(), viewerId);

        if (comment.getDeletedAt() == null) {
            comment.setDeletedAt(Instant.now());
            comments.save(comment);
            likes.deleteAll(likes.findByCommentId(commentId));
        }
    }

    @Transactional
    public Map<String, Object> addReview(String postId, NewReviewInput input, String viewerId) {
        String authorId = requireUser(viewerId);
        SocialPost post = requireInteractivePost(postId);
        requireItemPost(post);

        String text = input.text().trim();
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reseña no puede estar vacía");
        }

        PostComment review = new PostComment();
        review.setId(UUID.randomUUID().toString());
        review.setPostId(postId);
        review.setAuthorId(authorId);
        review.setKind("review");
        review.setText(text);
        review.setAuthorScore(input.authorScore());
        review.setModerationStatus("approved");
        comments.save(review);
        if (input.authorScore() != null) {
            PostRating rating = ratings.findByPostIdAndUserId(postId, authorId).orElseGet(() -> {
                PostRating created = new PostRating();
                created.setId(UUID.randomUUID().toString());
                created.setPostId(postId);
                created.setUserId(authorId);
                return created;
            });
            rating.setScore(input.authorScore());
            ratings.saveAndFlush(rating);
            syncCatalogRatings(post);
        }
        return presentComment(review, viewerId);
    }

    @Transactional
    public Map<String, Object> toggleVote(String postId, String viewerId) {
        requireUser(viewerId);
        SocialPost post = requireInteractivePost(postId);
        votes.findByPostIdAndUserId(postId, viewerId).ifPresentOrElse(
                votes::delete,
                () -> {
                    PostVote vote = new PostVote();
                    vote.setId(UUID.randomUUID().toString());
                    vote.setPostId(postId);
                    vote.setUserId(viewerId);
                    votes.save(vote);
                }
        );
        return present(post, viewerId);
    }

    @Transactional
    public Map<String, Object> rate(String postId, RatingInput input, String viewerId) {
        requireUser(viewerId);
        SocialPost post = requireInteractivePost(postId);
        requireItemPost(post);
        PostRating rating = ratings.findByPostIdAndUserId(postId, viewerId).orElseGet(() -> {
            PostRating newRating = new PostRating();
            newRating.setId(UUID.randomUUID().toString());
            newRating.setPostId(postId);
            newRating.setUserId(viewerId);
            return newRating;
        });
        rating.setScore(input.score());
        ratings.saveAndFlush(rating);
        syncCatalogRatings(post);
        return present(post, viewerId);
    }

    @Transactional
    public Map<String, Object> removeRating(String postId, String viewerId) {
        requireUser(viewerId);
        SocialPost post = requireInteractivePost(postId);
        requireItemPost(post);
        ratings.findByPostIdAndUserId(postId, viewerId).ifPresent(ratings::delete);
        ratings.flush();
        syncCatalogRatings(post);
        return present(post, viewerId);
    }

    @Transactional
    public Map<String, Object> share(String postId, ShareInput input, String viewerId) {
        requireUser(viewerId);
        SocialPost post = requireInteractivePost(postId);
        identityClient.getUser(input.recipientId());
        PostShare share = new PostShare();
        share.setId(UUID.randomUUID().toString());
        share.setPostId(postId);
        share.setSenderId(viewerId);
        share.setRecipientId(input.recipientId());
        shares.save(share);
        return present(post, viewerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> comments(String postId, String viewerId) {
        SocialPost post = requirePost(postId);
        requireVisible(post, viewerId);
        List<PostComment> visibleComments = comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(comment -> canView(comment, viewerId))
                .toList();
        java.util.Set<String> visibleIds = visibleComments.stream().map(PostComment::getId)
                .collect(java.util.stream.Collectors.toSet());
        return visibleComments.stream()
                .filter(comment -> comment.getParentId() == null || visibleIds.contains(comment.getParentId()))
                .map(comment -> presentComment(comment, viewerId)).toList();
    }

    @Transactional
    public Map<String, Object> addComment(String postId, NewCommentInput input, String viewerId) {
        String authorId = requireUser(viewerId);
        requireInteractivePost(postId);
        if (input.parentId() != null) {
            PostComment parent = requireComment(input.parentId());
            if (!"approved".equals(parent.getModerationStatus())
                    || !parent.getPostId().equals(postId)
                    || parent.getParentId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se admite un nivel de respuestas");
            }
        }

        String text = input.text().trim();
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentario vacío");
        }
        PostComment comment = new PostComment();
        comment.setId(UUID.randomUUID().toString());
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setParentId(input.parentId());
        comment.setReplyToUsername(input.replyToUsername());
        comment.setText(text);
        comment.setModerationStatus("approved");
        comments.save(comment);
        return presentComment(comment, viewerId);
    }

    @Transactional
    public Map<String, Object> toggleCommentLike(String commentId, String viewerId) {
        requireUser(viewerId);
        PostComment comment = requireComment(commentId);
        if (!"approved".equals(comment.getModerationStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario no encontrado");
        }
        requireInteractivePost(comment.getPostId());
        likes.findByCommentIdAndUserId(commentId, viewerId).ifPresentOrElse(
                likes::delete,
                () -> {
                    CommentLike like = new CommentLike();
                    like.setId(UUID.randomUUID().toString());
                    like.setCommentId(commentId);
                    like.setUserId(viewerId);
                    likes.save(like);
                }
        );
        return presentComment(comment, viewerId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countPostsByAuthor(String userId, String key) {
        requireInternal(key);
        return Map.of("count", posts.countByAuthorId(userId));
    }

    private Map<String, Object> present(SocialPost post, String viewerId) {
        JsonNode item = catalogClient.getItem(post.getItemId(), viewerId);
        JsonNode author = identityClient.getUser(post.getAuthorId());
        List<PostRating> postRatings = ratings.findByPostId(post.getId());
        double average = postRatings.stream().mapToDouble(rating -> rating.getScore()).average().orElse(
                "item".equals(post.getKind()) ? item.path("averageRating").asDouble(0.0) : 0.0);
        int ratingsCount = postRatings.isEmpty() && "item".equals(post.getKind())
                ? item.path("ratingsCount").asInt(0)
                : postRatings.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", post.getId());
        result.put("kind", post.getKind());
        result.put("category", key(item, "category"));
        result.put("author", publicAuthor(author));
        result.put("caption", post.getCaption());
        List<Map<String, Object>> attachedMedia = postMediaService.mediaForPost(post.getId(), viewerId);
        Map<String, Object> uploadedCover = attachedMedia.stream()
                .filter(media -> Boolean.TRUE.equals(media.get("isCover")))
                .findFirst()
                .orElse(null);
        String cover = uploadedCover == null ? key(item, "coverUrl") : (String) uploadedCover.get("url");
        List<Map<String, Object>> mediaItems = new java.util.ArrayList<>();
        if (uploadedCover == null && !cover.isBlank()) {
            mediaItems.add(Map.of(
                    "id", post.getId() + "-cover",
                    "url", cover,
                    "type", "image",
                    "isCover", true
            ));
        }
        mediaItems.addAll(attachedMedia);
        result.put("media", mediaItems);
        if (item.isObject()) {
            ObjectNode presentedItem = (ObjectNode) item.deepCopy();
            presentedItem.put("coverUrl", cover);
            result.put("rankableItem", presentedItem);
        } else {
            result.put("rankableItem", item);
        }
        if ("review".equals(post.getKind())) {
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("authorScore", post.getReviewScore());
            result.put("review", review);
        }
        result.put("votesCount", votes.countByPostId(post.getId()));
        result.put("votedByCurrentUser", viewerId != null
                && votes.findByPostIdAndUserId(post.getId(), viewerId).isPresent());
        result.put("averageRating", average);
        result.put("ratingsCount", ratingsCount);
        if (viewerId != null) {
            ratings.findByPostIdAndUserId(post.getId(), viewerId)
                    .ifPresent(rating -> result.put("currentUserRating", rating.getScore()));
        }
        result.put("commentsCount", comments.countByPostIdAndModerationStatusAndDeletedAtIsNull(
                post.getId(), "approved"));
        result.put("sharesCount", shares.countByPostId(post.getId()));
        result.put("moderationStatus", post.getModerationStatus());
        result.put("createdAt", post.getCreatedAt().toString());
        result.put("isReelEligible", false);
        return result;
    }

    private Map<String, Object> presentComment(PostComment comment, String viewerId) {
        JsonNode author = identityClient.getUser(comment.getAuthorId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("postId", comment.getPostId());
        if (comment.getParentId() != null) {
            result.put("parentId", comment.getParentId());
        }
        if (comment.getReplyToUsername() != null) {
            result.put("replyToUsername", comment.getReplyToUsername());
        }
        result.put("author", Map.of(
                "id", key(author, "id"),
                "username", key(author, "username"),
                "avatarUrl", key(author, "avatarUrl")
        ));
        boolean deleted = comment.getDeletedAt() != null;
        result.put("text", deleted ? "Comentario eliminado" : comment.getText());
        result.put("kind", comment.getKind());
        result.put("isDeleted", deleted);
        result.put("moderationStatus", comment.getModerationStatus());
        if ("review".equals(comment.getKind())) {
            result.put("authorScore", deleted ? null : comment.getAuthorScore());
        }
        result.put("likesCount", deleted ? 0 : likes.countByCommentId(comment.getId()));
        result.put("likedByCurrentUser", !deleted && viewerId != null
                && likes.findByCommentIdAndUserId(comment.getId(), viewerId).isPresent());
        result.put("createdAt", comment.getCreatedAt().toString());
        return result;
    }

    private Object publicAuthor(JsonNode author) {
        return Map.of(
                "id", key(author, "id"),
                "username", key(author, "username"),
                "displayName", key(author, "displayName"),
                "avatarUrl", key(author, "avatarUrl"),
                "accountType", key(author, "accountType"),
                "isVerified", author.path("isVerified").asBoolean(false)
        );
    }

    private void syncCatalogRatings(SocialPost post) {
        if (!"item".equals(post.getKind())) {
            return;
        }
        List<PostRating> postRatings = ratings.findByPostId(post.getId());
        double average = postRatings.stream().mapToDouble(rating -> rating.getScore()).average().orElse(0.0);
        catalogClient.updateRatingStats(post.getItemId(), average, postRatings.size());
    }

    private SocialPost requirePost(String id) {
        return posts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
    }

    private SocialPost requireInteractivePost(String id) {
        SocialPost post = requirePost(id);
        if (!"approved".equals(post.getModerationStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada");
        }
        return post;
    }

    private void requireVisible(SocialPost post, String viewerId) {
        if (!canView(post, viewerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada");
        }
    }

    private boolean canView(SocialPost post, String viewerId) {
        return "approved".equals(post.getModerationStatus())
                || (viewerId != null && viewerId.equals(post.getAuthorId()))
                || isAdmin(viewerId);
    }

    private boolean canView(PostComment comment, String viewerId) {
        return "approved".equals(comment.getModerationStatus())
                || (viewerId != null && viewerId.equals(comment.getAuthorId()))
                || isAdmin(viewerId);
    }

    private boolean isAdmin(String viewerId) {
        return viewerId != null && !viewerId.isBlank()
                && "admin".equals(key(identityClient.getUser(viewerId), "role"));
    }

    private void requireItemPost(SocialPost post) {
        if (!"item".equals(post.getKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La operación debe asociarse al post del Rankable Item");
        }
    }

    private PostComment requireComment(String id) {
        return comments.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario no encontrado"));
    }

    private void requireOwnerOrAdmin(String ownerId, String viewerId) {
        String currentUserId = requireUser(viewerId);
        if (currentUserId.equals(ownerId)) {
            return;
        }
        String role = key(identityClient.getUser(currentUserId), "role");
        if (!"admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar este contenido");
        }
    }

    private void afterCommit(Runnable action, String resource, String resourceId) {
        Runnable cleanup = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.error("No se pudo eliminar el recurso asociado {} {}", resource, resourceId, exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup.run();
            }
        });
    }

    private String requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión");
        }
        return userId;
    }

    private void validateKind(String kind) {
        if (!"item".equals(kind) && !"review".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind inválido");
        }
    }

    private void requireInternal(String key) {
        if (internalKey.isBlank() || !internalKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void registerItemCompensation(String postId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    catalogClient.deleteCreatedItem(postId);
                } catch (RuntimeException exception) {
                    log.error("No se pudo revertir Rankable Item asociado al post {}", postId, exception);
                }
            }
        });
    }

    private String key(JsonNode node, String field) {
        return node.path(field).asString("");
    }
}
