package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.dto.SocialDtos.NewCreatorInput;
import cl.rankkings.social_service.dto.SocialDtos.NewPostInput;
import cl.rankkings.social_service.dto.SocialDtos.NewRankableItemInput;
import cl.rankkings.social_service.dto.SocialDtos.NewReviewInput;
import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.LikeRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import cl.rankkings.social_service.repository.ShareRepository;
import cl.rankkings.social_service.repository.VoteRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(ratings.findByPostIdAndUserId("post-1", "reviewer")).thenReturn(Optional.empty());

        socialService.addReview("post-1", new NewReviewInput("Buena obra", null), "reviewer");

        ArgumentCaptor<PostComment> review = ArgumentCaptor.forClass(PostComment.class);
        verify(comments).save(review.capture());
        assertEquals("post-1", review.getValue().getPostId());
        assertEquals("reviewer", review.getValue().getAuthorId());
        assertEquals("review", review.getValue().getKind());
        assertEquals("Buena obra", review.getValue().getText());
    }

    @Test
    void rejectsStandaloneReviewPosts() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                socialService.createPost(new NewPostInput("item-1", null, "review", "Reseña"), "reviewer"));
    }

    @Test
    void createsRankableItemAndPostWithoutCoverOrExternalLinks() {
        NewRankableItemInput itemInput = new NewRankableItemInput(
                "books",
                "Libro",
                null,
                new NewCreatorInput(null, "Autora", null, null, null),
                null,
                null,
                null,
                Map.of("format", "novel"),
                null
        );
        NewPostInput postInput = new NewPostInput(null, itemInput, "item", "Mi publicación");
        JsonNode createdItem = org.mockito.Mockito.mock(JsonNode.class);
        stubString(createdItem, "id", "generated-item-id");
        JsonNode presentedItem = itemNode();
        stubString(createdItem, "postId", "");
        when(catalogClient.createItem(eq(itemInput), eq("reviewer"), anyString())).thenReturn(createdItem);
        when(catalogClient.getItem("generated-item-id", "reviewer")).thenReturn(presentedItem);
        when(posts.existsByItemIdAndKind("generated-item-id", "item")).thenReturn(false);
        when(posts.existsById(anyString())).thenReturn(false);
        JsonNode author = authorNode();
        when(identityClient.getUser("reviewer")).thenReturn(author);
        when(ratings.findByPostId(anyString())).thenReturn(List.of());
        when(ratings.findByPostIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());
        when(votes.countByPostId(anyString())).thenReturn(0L);
        when(votes.findByPostIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());
        when(comments.countByPostId(anyString())).thenReturn(0L);
        when(shares.countByPostId(anyString())).thenReturn(0L);
        when(postMediaService.mediaForPost(anyString())).thenReturn(List.of());

        socialService.createPost(postInput, "reviewer");

        ArgumentCaptor<SocialPost> post = ArgumentCaptor.forClass(SocialPost.class);
        verify(posts).save(post.capture());
        assertEquals("generated-item-id", post.getValue().getItemId());
        assertEquals("reviewer", post.getValue().getAuthorId());
        assertEquals("item", post.getValue().getKind());
        UUID.fromString(post.getValue().getId());
        verify(catalogClient).createItem(eq(itemInput), eq("reviewer"), eq(post.getValue().getId()));
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
        JsonNode author = authorNode();
        when(identityClient.getUser("owner")).thenReturn(author);
        when(ratings.findByPostId("post-1")).thenReturn(java.util.List.of());
        when(votes.countByPostId("post-1")).thenReturn(0L);
        when(comments.countByPostId("post-1")).thenReturn(0L);
        when(shares.countByPostId("post-1")).thenReturn(0L);
        when(postMediaService.mediaForPost("post-1")).thenReturn(java.util.List.of());

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

    private JsonNode authorNode() {
        JsonNode author = org.mockito.Mockito.mock(JsonNode.class);
        stubString(author, "id", "reviewer");
        stubString(author, "username", "ana");
        stubString(author, "displayName", "Ana");
        stubString(author, "avatarUrl", "");
        stubString(author, "accountType", "user");
        JsonNode verified = org.mockito.Mockito.mock(JsonNode.class);
        when(author.path("isVerifiedBusiness")).thenReturn(verified);
        when(verified.asBoolean(false)).thenReturn(false);
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
