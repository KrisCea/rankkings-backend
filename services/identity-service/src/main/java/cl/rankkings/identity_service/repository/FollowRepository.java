package cl.rankkings.identity_service.repository;

import cl.rankkings.identity_service.model.FollowRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<FollowRelation, String> {
    Optional<FollowRelation> findByFollowerIdAndFollowedId(String followerId, String followedId);
    boolean existsByFollowerIdAndFollowedId(String followerId, String followedId);
    long countByFollowerId(String id);
    long countByFollowedId(String id);
    List<FollowRelation> findByFollowerId(String id);
}
