package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShareRepository extends JpaRepository<PostShare, String> {
    long countByPostId(String postId);
    List<PostShare> findByPostId(String postId);
}
