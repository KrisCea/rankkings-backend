package cl.rankkings.social_service;
import cl.rankkings.social_service.model.PostVote;
import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class SocialPersistenceTest {
    @Autowired PostRepository posts;
    @Autowired VoteRepository votes;
    @Autowired CommentRepository comments;
    @Test void persistsPostsAndVotes() {
        SocialPost p=new SocialPost();p.setId(UUID.randomUUID().toString());p.setAuthorId("user-demo");p.setItemId("item-demo");p.setKind("review");p.setCaption("Reseña de prueba");
        posts.saveAndFlush(p);
        PostVote v=new PostVote();v.setId(UUID.randomUUID().toString());v.setPostId(p.getId());v.setUserId("user-demo");
        votes.saveAndFlush(v);
        assertEquals(1,votes.countByPostId(p.getId()));
        assertEquals("review",posts.findById(p.getId()).orElseThrow().getKind());

        PostComment review = new PostComment();
        review.setId(UUID.randomUUID().toString());
        review.setPostId(p.getId());
        review.setAuthorId("user-demo");
        review.setKind("review");
        review.setText("Reseña como comentario");
        review.setAuthorScore(8.5);
        comments.saveAndFlush(review);
        assertEquals("review", comments.findById(review.getId()).orElseThrow().getKind());
        assertEquals(8.5, comments.findById(review.getId()).orElseThrow().getAuthorScore());
        review.setDeletedAt(Instant.now());
        comments.saveAndFlush(review);
        assertTrue(comments.findByIdAndDeletedAtIsNull(review.getId()).isEmpty());

        PostComment unscoredReview = new PostComment();
        unscoredReview.setId(UUID.randomUUID().toString());
        unscoredReview.setPostId(p.getId());
        unscoredReview.setAuthorId("user-demo");
        unscoredReview.setKind("review");
        unscoredReview.setText("Reseña sin puntuación");
        comments.saveAndFlush(unscoredReview);
        assertNull(comments.findById(unscoredReview.getId()).orElseThrow().getAuthorScore());
        assertEquals(1, comments.countByPostIdAndDeletedAtIsNull(p.getId()));
    }
}
