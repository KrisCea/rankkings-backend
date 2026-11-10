package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostShare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<PostShare, String> {
    long countByPostId(String postId);
}
