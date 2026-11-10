package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<PostRating, String> {
    Optional<PostRating> findByPostIdAndUserId(String postId, String userId);
    List<PostRating> findByPostId(String postId);
}
