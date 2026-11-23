package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.SocialPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<SocialPost, String> {
    List<SocialPost> findAllByOrderByCreatedAtDesc();
    List<SocialPost> findByModerationStatusOrderByCreatedAtDesc(String moderationStatus);
    List<SocialPost> findByAuthorIdAndKindOrderByCreatedAtDesc(String authorId, String kind);
    List<SocialPost> findByItemIdAndKindOrderByCreatedAtDesc(String itemId, String kind);
    Optional<SocialPost> findFirstByItemIdAndKindOrderByCreatedAtAsc(String itemId, String kind);
    boolean existsByItemIdAndKind(String itemId, String kind);
    long countByAuthorId(String authorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from SocialPost post where post.id = :postId")
    Optional<SocialPost> findByIdForUpdate(@Param("postId") String postId);
}
