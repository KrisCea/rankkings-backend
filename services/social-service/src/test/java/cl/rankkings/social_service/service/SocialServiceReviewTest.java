package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.dto.SocialDtos.NewCreatorInput;
import cl.rankkings.social_service.dto.SocialDtos.NewCommentInput;
import cl.rankkings.social_service.dto.SocialDtos.NewPostInput;
import cl.rankkings.social_service.dto.SocialDtos.NewRankableItemInput;
import cl.rankkings.social_service.dto.SocialDtos.NewReviewInput;
import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.PostRating;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.LikeRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import cl.rankkings.social_service.repository.ShareRepository;
import cl.rankkings.social_service.repository.VoteRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialServiceReviewTest {
    @Mock private PostRepository posts;
    @Mock private VoteRepository votes;
    @Mock private RatingRepository ratings;
    @Mock private CommentRepository comments;
    @Mock private LikeRepository likes;
    @Mock private ShareRepository shares;
    @Mock private CatalogServiceClient catalogClient;
    @Mock private IdentityServiceClient identityClient;
    @Mock private PostMediaService postMediaService;
    @InjectMocks private SocialService socialService;

    @Test
    void createsReviewsAsCommentsOfTheRankableItemPost() {
        SocialPost itemPost = new SocialPost();
        itemPost.setId("post-1");
        itemPost.setKind("item");
        itemPost.setItemId("item-1");
        itemPost.setAuthorId("owner");
        when(posts.findById("post-1")).thenReturn(Optional.of(itemPost));
        JsonNode author = commentAuthorNode();
        when(identityClient.getUser("reviewer")).thenReturn(author);
        when(likes.countByCommentId(org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);
        when(likes.findByCommentIdAndUserId(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        Map<String, Object> response = socialService.addReview(
                "post-1", new NewReviewInput("Buena obra", null), "reviewer");

        ArgumentCaptor<PostComment> review = ArgumentCaptor.forClass(PostComment.class);
        verify(comments).save(review.capture());
        assertEquals("post-1", review.getValue().getPostId());
        assertEquals("reviewer", review.getValue().getAuthorId());
        assertEquals("review", review.getValue().getKind());
        assertEquals("Buena obra", review.getValue().getText());
        assertEquals("approved", review.getValue().getModerationStatus());
        assertNull(review.getValue().getAuthorScore());
        assertTrue(response.containsKey("authorScore"));
        assertNull(response.get("authorScore"));
        verify(ratings, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(PostRating.class));
        verify(catalogClient, never()).updateRatingStats(anyString(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void appliesReviewScoreToRatingsImmediately() {
        SocialPost itemPost = new SocialPost();
        itemPost.setId("post-1");
        itemPost.setKind("item");
        itemPost.setItemId("item-1");
        itemPost.setAuthorId("owner");
        when(posts.findById("post-1")).thenReturn(Optional.of(itemPost));
        JsonNode author = commentAuthorNode();
        when(identityClient.getUser("reviewer")).thenReturn(author);
        when(likes.countByCommentId(anyString())).thenReturn(0L);
        when(likes.findByCommentIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());
        PostRating existingRating = new PostRating();
        existingRating.setId("rating-1");
        existingRating.setPostId("post-1");
        existingRating.setUserId("reviewer");
        when(ratings.findByPostIdAndUserId("post-1", "reviewer")).thenReturn(Optional.of(existingRating));
        when(ratings.findByPostId("post-1")).thenReturn(List.of(existingRating));

        Map<String, Object> response = socialService.addReview(
                "post-1", new NewReviewInput("Buena obra", 8.5), "reviewer");

        ArgumentCaptor<PostComment> comment = ArgumentCaptor.forClass(PostComment.class);
        verify(comments).save(comment.capture());
        assertEquals(8.5, comment.getValue().getAuthorScore());
        assertEquals("approved", comment.getValue().getModerationStatus());
        assertEquals(8.5, response.get("authorScore"));
        verify(ratings).saveAndFlush(existingRating);
        verify(catalogClient).updateRatingStats(eq("item-1"), org.mockito.ArgumentMatchers.eq(8.5),
                org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void publishesNewCommentsWithoutModeration() {
        SocialPost itemPost = new SocialPost();
        itemPost.setId("post-1");
        itemPost.setKind("item");
        itemPost.setModerationStatus("approved");
        when(posts.findById("post-1")).thenReturn(Optional.of(itemPost));
        JsonNode author = commentAuthorNode();
        when(identityClient.getUser("commenter")).thenReturn(author);
        when(likes.countByCommentId(anyString())).thenReturn(0L);
        when(likes.findByCommentIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());

        socialService.addComment("post-1", new NewCommentInput("Hola", null, null), "commenter");

        ArgumentCaptor<PostComment> comment = ArgumentCaptor.forClass(PostComment.class);
        verify(comments).save(comment.capture());
        assertEquals("comment", comment.getValue().getKind());
        assertEquals("approved", comment.getValue().getModerationStatus());
    }

    @Test
    void validatesOptionalReviewScoreRange() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new NewReviewInput("Sin nota", null)).isEmpty());
            assertTrue(validator.validate(new NewReviewInput("Nota mínima", 1.0)).isEmpty());
            assertTrue(validator.validate(new NewReviewInput("Nota máxima", 10.0)).isEmpty());
            assertFalse(validator.validate(new NewReviewInput("Nota baja", 0.0)).isEmpty());
            assertFalse(validator.validate(new NewReviewInput("Nota alta", 10.1)).isEmpty());
        }
    }

    @Test
    void includesNullScoreInLegacyReviewPostPayload() {
        SocialPost reviewPost = new SocialPost();
        reviewPost.setId("post-1");
        reviewPost.setKind("review");
        reviewPost.setItemId("item-1");
        reviewPost.setAuthorId("reviewer");
        when(posts.findById("post-1")).thenReturn(Optional.of(reviewPost));
        JsonNode item = org.mockito.Mockito.mock(JsonNode.class);
        stubString(item, "category", "music");
        stubString(item, "coverUrl", "");
        JsonNode author = authorNode(false);
        when(catalogClient.getItem("item-1", null)).thenReturn(item);
        when(identityClient.getUser("reviewer")).thenReturn(author);
        when(ratings.findByPostId("post-1")).thenReturn(List.of());
        when(postMediaService.mediaForPost("post-1", null)).thenReturn(List.of());
        when(votes.countByPostId("post-1")).thenReturn(0L);
        when(comments.countByPostIdAndModerationStatusAndDeletedAtIsNull("post-1", "approved")).thenReturn(0L);
        when(shares.countByPostId("post-1")).thenReturn(0L);

        Map<String, Object> response = socialService.getPost("post-1", null);

        Map<?, ?> review = assertInstanceOf(Map.class, response.get("review"));
        assertTrue(review.containsKey("authorScore"));
        assertNull(review.get("authorScore"));
    }

    @Test
    void rejectsStandaloneReviewPosts() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                socialService.createPost(new NewPostInput("item-1", null, "review", "Reseña"), "reviewer"));
    }

    @Test
    void hidesPendingPostsFromVisitors() {
        SocialPost pendingPost = new SocialPost();
        pendingPost.setId("post-1");
        pendingPost.setAuthorId("author");
        pendingPost.setModerationStatus("pending");
        when(posts.findById("post-1")).thenReturn(Optional.of(pendingPost));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> socialService.getPost("post-1", null));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void createsRankableItemAndPostWithoutCoverOrExternalLinks() {
        NewRankableItemInput itemInput = new NewRankableItemInput(
                "books", "Libro", null, null, null, null, null, null, null, "Autora");
        NewPostInput postInput = new NewPostInput(null, itemInput, "item", "Mi publicación");
        JsonNode createdItem = org.mockito.Mockito.mock(JsonNode.class);
        stubString(createdItem, "id", "generated-item-id");
        JsonNode presentedItem = itemNode();
        stubString(createdItem, "postId", "");
        when(catalogClient.createItem(eq(itemInput), eq("reviewer"), anyString(), eq(true))).thenReturn(createdItem);
        when(catalogClient.getItem("generated-item-id", "reviewer")).thenReturn(presentedItem);
        when(posts.existsByItemIdAndKind("generated-item-id", "item")).thenReturn(false);
        when(posts.existsById(anyString())).thenReturn(false);
        JsonNode author = authorNode(true);
        when(identityClient.getUser("reviewer")).thenReturn(author);
        when(ratings.findByPostId(anyString())).thenReturn(List.of());
        when(ratings.findByPostIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());
        when(votes.countByPostId(anyString())).thenReturn(0L);
        when(votes.findByPostIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());
        when(comments.countByPostIdAndModerationStatusAndDeletedAtIsNull(anyString(), eq("approved"))).thenReturn(0L);
        when(shares.countByPostId(anyString())).thenReturn(0L);
        when(postMediaService.mediaForPost(anyString(), anyString())).thenReturn(List.of());

        socialService.createPost(postInput, "reviewer");

        ArgumentCaptor<SocialPost> post = ArgumentCaptor.forClass(SocialPost.class);
        verify(posts).saveAndFlush(post.capture());
        assertEquals("generated-item-id", post.getValue().getItemId());
        assertEquals("reviewer", post.getValue().getAuthorId());
        assertEquals("item", post.getValue().getKind());
        assertEquals("approved", post.getValue().getModerationStatus());
        UUID.fromString(post.getValue().getId());
        verify(catalogClient).createItem(eq(itemInput), eq("reviewer"), eq(post.getValue().getId()), eq(true));
        assertEquals("Autora", itemInput.creatorInput().name());
    }

    @Test
    void resolvesTheExistingItemPostByItemId() {
        SocialPost itemPost = new SocialPost();
        itemPost.setId("post-1");
        itemPost.setItemId("item-1");
        itemPost.setKind("item");
        itemPost.setAuthorId("owner");
        when(posts.findFirstByItemIdAndKindOrderByCreatedAtAsc("item-1", "item"))
                .thenReturn(Optional.of(itemPost));
        JsonNode item = itemNode();
        when(catalogClient.getItem("item-1", "viewer")).thenReturn(item);
        JsonNode author = authorNode(false);
        when(identityClient.getUser("owner")).thenReturn(author);
        when(ratings.findByPostId("post-1")).thenReturn(java.util.List.of());
        when(votes.countByPostId("post-1")).thenReturn(0L);
        when(comments.countByPostIdAndModerationStatusAndDeletedAtIsNull("post-1", "approved")).thenReturn(0L);
        when(shares.countByPostId("post-1")).thenReturn(0L);
        when(postMediaService.mediaForPost("post-1", "viewer")).thenReturn(java.util.List.of());

        var result = socialService.getItemPost("item-1", "viewer");

        assertEquals("post-1", result.get("id"));
        assertEquals("item", result.get("kind"));
    }

    @Test
    void returnsNotFoundWhenItemHasNoSocialPost() {
        when(posts.findFirstByItemIdAndKindOrderByCreatedAtAsc("item-1", "item"))
                .thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                socialService.getItemPost("item-1", "viewer"));
        verify(catalogClient, never()).getItem("item-1", "viewer");
    }

    private JsonNode authorNode(boolean verifiedAccount) {
        JsonNode author = org.mockito.Mockito.mock(JsonNode.class);
        stubString(author, "id", "reviewer");
        stubString(author, "username", "ana");
        stubString(author, "displayName", "Ana");
        stubString(author, "avatarUrl", "");
        stubString(author, "accountType", "user");
        JsonNode verified = org.mockito.Mockito.mock(JsonNode.class);
        when(author.path("isVerified")).thenReturn(verified);
        when(verified.asBoolean(false)).thenReturn(verifiedAccount);
        return author;
    }

    private JsonNode commentAuthorNode() {
        JsonNode author = org.mockito.Mockito.mock(JsonNode.class);
        stubString(author, "id", "reviewer");
        stubString(author, "username", "ana");
        stubString(author, "avatarUrl", "");
        return author;
    }

    private JsonNode itemNode() {
        JsonNode item = org.mockito.Mockito.mock(JsonNode.class);
        stubString(item, "category", "music");
        stubString(item, "coverUrl", "");
        JsonNode ratingsCount = org.mockito.Mockito.mock(JsonNode.class);
        when(item.path("ratingsCount")).thenReturn(ratingsCount);
        when(ratingsCount.asInt(0)).thenReturn(0);
        JsonNode averageRating = org.mockito.Mockito.mock(JsonNode.class);
        when(item.path("averageRating")).thenReturn(averageRating);
        when(averageRating.asDouble(0.0)).thenReturn(0.0);
        return item;
    }

    private void stubString(JsonNode node, String field, String value) {
        JsonNode child = org.mockito.Mockito.mock(JsonNode.class);
        when(node.path(field)).thenReturn(child);
        when(child.asString("")).thenReturn(value);
    }
}
