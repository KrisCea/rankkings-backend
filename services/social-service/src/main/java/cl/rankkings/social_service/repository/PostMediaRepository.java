package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostMediaRepository extends JpaRepository<PostMedia, String> {
    List<PostMedia> findByPostIdOrderByCreatedAtAsc(String postId);
    List<PostMedia> findByPostIdAndStatusOrderByCreatedAtAsc(String postId, String status);
    Optional<PostMedia> findByIdAndPostId(String id, String postId);
    long countByPostId(String postId);
}
