package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.PostRating;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Mock private PostRepository posts;
    @Mock private CommentRepository comments;
    @Mock private RatingRepository ratings;
    @Mock private PostMediaService postMediaService;
    @Mock private CatalogServiceClient catalogClient;
    @Mock private IdentityServiceClient identityClient;
    @InjectMocks private ModerationService moderationService;

    @Test
    void deniesQueueAccessToNonAdmins() throws Exception {
        identityServiceUser("user-1", "{\"role\":\"user\"}");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> moderationService.queue("pending", "all", 25, null, "user-1"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void rejectsMalformedCursors() throws Exception {
        identityServiceUser("admin-1", "{\"role\":\"admin\"}");
        when(posts.findByModerationStatusOrderByCreatedAtDesc("pending")).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> moderationService.queue("pending", "post", 25, "bm90LWFuLWluc3RhbnR8cG9zdHx4", "admin-1"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void returnsNestedDiscriminatedQueueItemsAndCursorPages() throws Exception {
        identityServiceUser("admin-1", "{\"role\":\"admin\"}");
        identityServiceUser("author-1", "{\"username\":\"author\"}");
        when(catalogClient.getItem("item-1", null)).thenReturn(mapper.readTree(
                "{\"id\":\"item-1\",\"title\":\"Album\",\"category\":\"music\",\"coverUrl\":null}"));
        SocialPost newer = post("post-new", "2026-01-02T00:00:00Z");
        SocialPost older = post("post-old", "2026-01-01T00:00:00Z");
        when(posts.findByModerationStatusOrderByCreatedAtDesc("pending")).thenReturn(List.of(older, newer));

        Map<String, Object> firstPage = moderationService.queue("pending", "post", 1, null, "admin-1");

        List<?> items = (List<?>) firstPage.get("items");
        Map<?, ?> first = assertInstanceOf(Map.class, items.get(0));
        assertEquals("post", first.get("type"));
        assertEquals("post-new", first.get("id"));
        assertEquals("author", ((Map<?, ?>) first.get("author")).get("username"));
        Map<?, ?> content = assertInstanceOf(Map.class, first.get("content"));
        assertEquals("Album", ((Map<?, ?>) content.get("item")).get("title"));
        Map<?, ?> page = assertInstanceOf(Map.class, firstPage.get("page"));
        assertTrue((Boolean) page.get("hasMore"));
        String cursor = (String) page.get("nextCursor");

        Map<String, Object> secondPage = moderationService.queue("pending", "post", 1, cursor, "admin-1");
        Map<?, ?> second = (Map<?, ?>) ((List<?>) secondPage.get("items")).get(0);
        assertEquals("post-old", second.get("id"));
        assertEquals(false, ((Map<?, ?>) secondPage.get("page")).get("hasMore"));
    }

    @Test
    void onlyAddsReviewScoreToRatingsAfterApproval() throws Exception {
        identityServiceUser("admin-1", "{\"role\":\"admin\",\"username\":\"moderator\"}");
        PostComment review = new PostComment();
        review.setId("review-1");
        review.setPostId("post-1");
        review.setAuthorId("author-1");
        review.setKind("review");
        review.setText("Good");
        review.setAuthorScore(8.5);
        review.setModerationStatus("pending");
        when(comments.findByIdAndDeletedAtIsNullForUpdate("review-1")).thenReturn(Optional.of(review));
        PostRating currentRating = new PostRating();
        currentRating.setId("rating-1");
        currentRating.setPostId("post-1");
        currentRating.setUserId("author-1");
        currentRating.setScore(7.0);
        when(ratings.findByPostIdAndUserId("post-1", "author-1")).thenReturn(Optional.of(currentRating));
        when(posts.findById("post-1")).thenReturn(Optional.of(itemPost()));
        when(ratings.findByPostId("post-1")).thenReturn(List.of(currentRating));

        Map<String, Object> result = moderationService.decide("review", "review-1", true, null, "admin-1");

        assertEquals("approved", result.get("status"));
        assertEquals("approved", review.getModerationStatus());
        ArgumentCaptor<PostRating> rating = ArgumentCaptor.forClass(PostRating.class);
        verify(ratings).saveAndFlush(rating.capture());
        assertEquals(8.5, rating.getValue().getScore());
        verify(catalogClient).updateRatingStats("item-1", 8.5, 1);
    }

    private SocialPost post(String id, String createdAt) {
        SocialPost post = new SocialPost();
        post.setId(id);
        post.setItemId("item-1");
        post.setAuthorId("author-1");
        post.setKind("item");
        post.setCaption("Caption");
        post.setCreatedAt(Instant.parse(createdAt));
        post.setModerationStatus("pending");
        return post;
    }

    private SocialPost itemPost() {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setItemId("item-1");
        post.setKind("item");
        return post;
    }

    private boolean identityServiceUser(String id, String json) throws Exception {
        when(identityClient.getUser(id)).thenReturn(mapper.readTree(json));
        return true;
    }
}
