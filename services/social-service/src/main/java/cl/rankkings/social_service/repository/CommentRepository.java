package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<PostComment, String> {
    List<PostComment> findByPostIdOrderByCreatedAtAsc(String postId);
    long countByPostId(String postId);
}
