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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
    @Value("${rankkings.internal-key:}") private String internalKey;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> feed(String viewerId) {
        return posts.findAllByOrderByCreatedAtDesc().stream().limit(100).map(post -> present(post, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> featured(String viewerId) {
        return posts.findAllByOrderByCreatedAtDesc().stream().limit(12).map(post -> present(post, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPost(String id, String viewerId) {
        return present(requirePost(id), viewerId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getItemPost(String itemId, String viewerId) {
        SocialPost post = posts.findFirstByItemIdAndKindOrderByCreatedAtAsc(itemId, "item")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La obra aún no tiene publicación"));
        return present(post, viewerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> userPosts(String username, String kind, String viewerId) {
        validateKind(kind);
        JsonNode user = identityClient.findUserByUsername(username);
        if ("review".equals(kind)) {
            return comments.findByAuthorIdAndKindOrderByCreatedAtDesc(key(user, "id"), "review")
                    .stream().limit(100).map(comment -> presentComment(comment, viewerId)).toList();
        }
        return posts.findByAuthorIdAndKindOrderByCreatedAtDesc(key(user, "id"), kind)
                .stream().limit(100).map(post -> present(post, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> itemReviews(String itemId, String viewerId) {
        catalogClient.getItem(itemId, null);
        return posts.findByItemIdAndKindOrderByCreatedAtDesc(itemId, "item").stream()
                .findFirst()
                .map(post -> comments.findByPostIdAndKindOrderByCreatedAtDesc(post.getId(), "review")
                        .stream().map(comment -> presentComment(comment, viewerId)).toList())
                .orElseGet(List::of);
    }

    @Transactional
    public Map<String, Object> createPost(NewPostInput input, String viewerId) {
        String authorId = requireUser(viewerId);
        validateKind(input.kind());
        if ("review".equals(input.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Crea la reseña como comentario del post del ítem");
        }

        JsonNode item = catalogClient.getItem(input.itemId(), null);
        if ("item".equals(input.kind()) && posts.existsByItemIdAndKind(input.itemId(), "item")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este ítem ya tiene publicación");
        }

        String catalogPostId = key(item, "postId");
        String postId = "item".equals(input.kind()) && !catalogPostId.isBlank()
                ? catalogPostId
                : UUID.randomUUID().toString();
        if (posts.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ID de publicación ocupado");
        }

        SocialPost post = new SocialPost();
        post.setId(postId);
        post.setAuthorId(authorId);
        post.setItemId(input.itemId());
        post.setKind(input.kind());
        post.setCaption(input.caption() == null ? "" : input.caption().trim());
        posts.save(post);
        return present(post, viewerId);
    }

    @Transactional
    public Map<String, Object> addReview(String postId, NewReviewInput input, String viewerId) {
        String authorId = requireUser(viewerId);
        SocialPost post = requirePost(postId);
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
        comments.save(review);

        if (input.authorScore() != null) {
            PostRating rating = ratings.findByPostIdAndUserId(postId, authorId).orElseGet(() -> {
                PostRating newRating = new PostRating();
                newRating.setId(UUID.randomUUID().toString());
                newRating.setPostId(postId);
                newRating.setUserId(authorId);
                return newRating;
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
        SocialPost post = requirePost(postId);
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
        SocialPost post = requirePost(postId);
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
        SocialPost post = requirePost(postId);
        requireItemPost(post);
        ratings.findByPostIdAndUserId(postId, viewerId).ifPresent(ratings::delete);
        ratings.flush();
        syncCatalogRatings(post);
        return present(post, viewerId);
    }

    @Transactional
    public Map<String, Object> share(String postId, ShareInput input, String viewerId) {
        requireUser(viewerId);
        SocialPost post = requirePost(postId);
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
        requirePost(postId);
        return comments.findByPostIdOrderByCreatedAtAsc(postId)
                .stream().map(comment -> presentComment(comment, viewerId)).toList();
    }

    @Transactional
    public Map<String, Object> addComment(String postId, NewCommentInput input, String viewerId) {
        String authorId = requireUser(viewerId);
        requirePost(postId);
        if (input.parentId() != null) {
            PostComment parent = requireComment(input.parentId());
            if (!parent.getPostId().equals(postId) || parent.getParentId() != null) {
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
        comments.save(comment);
        return presentComment(comment, viewerId);
    }

    @Transactional
    public Map<String, Object> toggleCommentLike(String commentId, String viewerId) {
        requireUser(viewerId);
        PostComment comment = requireComment(commentId);
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
        List<Map<String, Object>> mediaItems = new java.util.ArrayList<>();
        String cover = key(item, "coverUrl");
        if (!cover.isBlank()) {
            mediaItems.add(Map.of("id", post.getId() + "-cover", "url", cover, "type", "image"));
        }
        mediaItems.addAll(postMediaService.mediaForPost(post.getId()));
        result.put("media", mediaItems);
        result.put("rankableItem", item);
        if (post.getReviewScore() != null) {
            result.put("review", Map.of("authorScore", post.getReviewScore()));
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
        result.put("commentsCount", comments.countByPostId(post.getId()));
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
        result.put("text", comment.getText());
        result.put("kind", comment.getKind());
        if ("review".equals(comment.getKind())) {
            ratings.findByPostIdAndUserId(comment.getPostId(), comment.getAuthorId())
                    .ifPresent(rating -> result.put("authorScore", rating.getScore()));
        }
        result.put("likesCount", likes.countByCommentId(comment.getId()));
        result.put("likedByCurrentUser", viewerId != null
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
                "isVerifiedBusiness", author.path("isVerifiedBusiness").asBoolean(false)
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

    private void requireItemPost(SocialPost post) {
        if (!"item".equals(post.getKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La operación debe asociarse al post del Rankable Item");
        }
    }

    private PostComment requireComment(String id) {
        return comments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario no encontrado"));
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

    private String key(JsonNode node, String field) {
        return node.path(field).asString("");
    }
}
