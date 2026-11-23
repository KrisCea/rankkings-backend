package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialServiceDeletionTest {
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
    void deletingPostCascadesThroughSocialDataAndItsItem() {
        SocialPost post = itemPost("post-1", "author");
        PostComment comment = comment("comment-1", "post-1", "reader", "comment");
        CommentLike like = new CommentLike();
        PostRating rating = new PostRating();
        PostVote vote = new PostVote();
        PostShare share = new PostShare();
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(post));
        when(comments.findByPostIdOrderByCreatedAtAsc("post-1")).thenReturn(List.of(comment));
        when(likes.findByCommentIdIn(List.of("comment-1"))).thenReturn(List.of(like));
        when(ratings.findByPostId("post-1")).thenReturn(List.of(rating));
        when(votes.findByPostId("post-1")).thenReturn(List.of(vote));
        when(shares.findByPostId("post-1")).thenReturn(List.of(share));

        socialService.deletePost("post-1", "author");

        verify(likes).deleteAll(List.of(like));
        verify(comments).deleteAll(List.of(comment));
        verify(ratings).deleteAll(List.of(rating));
        verify(votes).deleteAll(List.of(vote));
        verify(shares).deleteAll(List.of(share));
        verify(posts).delete(post);
        verify(postMediaService).deleteForPost("post-1");
        verify(catalogClient).deleteCreatedItem("post-1");
    }

    @Test
    void rejectsDeletingAnotherAuthorsPostForNonAdmin() throws Exception {
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(itemPost("post-1", "author")));
        when(identityClient.getUser("reader"))
                .thenReturn(new ObjectMapper().readTree("{\"role\":\"user\"}"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> socialService.deletePost("post-1", "reader"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(posts, never()).delete(org.mockito.ArgumentMatchers.any(SocialPost.class));
    }

    @Test
    void rejectsAnonymousCommentDeletion() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class,
                        () -> socialService.deleteComment("comment-1", null)).getStatusCode());
    }

    @Test
    void deletingReviewSoftDeletesItAndRemovesItsRating() {
        SocialPost post = itemPost("post-1", "owner");
        PostComment review = comment("review-1", "post-1", "reviewer", "review");
        review.setAuthorScore(8.0);
        PostRating rating = new PostRating();
        when(posts.findById("post-1")).thenReturn(Optional.of(post));
        when(comments.findById("review-1")).thenReturn(Optional.of(review));
        when(likes.findByCommentId("review-1")).thenReturn(List.of());
        when(ratings.findByPostIdAndUserId("post-1", "reviewer")).thenReturn(Optional.of(rating));
        when(ratings.findByPostId("post-1")).thenReturn(List.of());

        socialService.deleteReview("post-1", "review-1", "reviewer");

        assertNotNull(review.getDeletedAt());
        verify(comments).save(review);
        verify(ratings).delete(rating);
        verify(catalogClient).updateRatingStats("item-1", 0.0, 0);
    }

    @Test
    void deletingUnscoredReviewPreservesSeparateRating() {
        SocialPost post = itemPost("post-1", "owner");
        PostComment review = comment("review-1", "post-1", "reviewer", "review");
        when(posts.findById("post-1")).thenReturn(Optional.of(post));
        when(comments.findById("review-1")).thenReturn(Optional.of(review));
        when(likes.findByCommentId("review-1")).thenReturn(List.of());

        socialService.deleteReview("post-1", "review-1", "reviewer");

        verify(ratings, never()).findByPostIdAndUserId("post-1", "reviewer");
        verify(ratings, never()).delete(org.mockito.ArgumentMatchers.any(PostRating.class));
        verify(catalogClient, never()).updateRatingStats(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void deletingCommentMarksItAndRemovesItsLikes() {
        PostComment comment = comment("comment-1", "post-1", "author", "comment");
        when(comments.findById("comment-1")).thenReturn(Optional.of(comment));
        when(likes.findByCommentId("comment-1")).thenReturn(List.of());

        socialService.deleteComment("comment-1", "author");

        assertNotNull(comment.getDeletedAt());
        verify(comments).save(comment);
    }

    @Test
    void allowsAdminToDeleteAnotherUsersComment() throws Exception {
        PostComment comment = comment("comment-1", "post-1", "author", "comment");
        when(comments.findById("comment-1")).thenReturn(Optional.of(comment));
        when(identityClient.getUser("admin-user"))
                .thenReturn(new ObjectMapper().readTree("{\"role\":\"admin\"}"));
        when(likes.findByCommentId("comment-1")).thenReturn(List.of());

        socialService.deleteComment("comment-1", "admin-user");

        assertNotNull(comment.getDeletedAt());
        verify(comments).save(comment);
    }

    private SocialPost itemPost(String id, String authorId) {
        SocialPost post = new SocialPost();
        post.setId(id);
        post.setItemId("item-1");
        post.setKind("item");
        post.setAuthorId(authorId);
        return post;
    }

    private PostComment comment(String id, String postId, String authorId, String kind) {
        PostComment comment = new PostComment();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setKind(kind);
        comment.setText("text");
        return comment;
    }
}
