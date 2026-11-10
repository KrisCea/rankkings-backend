package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<SocialPost, String> {
    List<SocialPost> findAllByOrderByCreatedAtDesc();
    List<SocialPost> findByAuthorIdAndKindOrderByCreatedAtDesc(String authorId, String kind);
    List<SocialPost> findByItemIdAndKindOrderByCreatedAtDesc(String itemId, String kind);
    boolean existsByItemIdAndKind(String itemId, String kind);
    long countByAuthorId(String authorId);
}
