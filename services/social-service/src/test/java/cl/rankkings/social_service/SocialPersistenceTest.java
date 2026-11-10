package cl.rankkings.social_service;
import cl.rankkings.social_service.model.PostVote;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class SocialPersistenceTest {
    @Autowired PostRepository posts;
    @Autowired VoteRepository votes;
    @Test void persistsPostsAndVotes() {
        SocialPost p=new SocialPost();p.setId(UUID.randomUUID().toString());p.setAuthorId("user-demo");p.setItemId("item-demo");p.setKind("review");p.setCaption("Reseña de prueba");
        posts.saveAndFlush(p);
        PostVote v=new PostVote();v.setId(UUID.randomUUID().toString());v.setPostId(p.getId());v.setUserId("user-demo");
        votes.saveAndFlush(v);
        assertEquals(1,votes.countByPostId(p.getId()));
        assertEquals("review",posts.findById(p.getId()).orElseThrow().getKind());
    }
}
