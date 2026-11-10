package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<CommentLike, String> {
    Optional<CommentLike> findByCommentIdAndUserId(String commentId, String userId);
    long countByCommentId(String commentId);
}
