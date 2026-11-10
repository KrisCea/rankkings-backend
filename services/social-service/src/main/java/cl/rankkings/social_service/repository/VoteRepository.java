package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoteRepository extends JpaRepository<PostVote, String> {
    Optional<PostVote> findByPostIdAndUserId(String postId, String userId);
    long countByPostId(String postId);
}
