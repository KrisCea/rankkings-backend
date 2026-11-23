package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostMedia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostMediaRepository extends JpaRepository<PostMedia, String> {
    List<PostMedia> findByPostIdOrderByCreatedAtAsc(String postId);
    List<PostMedia> findByPostIdAndStatusOrderByCreatedAtAsc(String postId, String status);
    List<PostMedia> findByStatusAndModerationStatusOrderByCreatedAtDesc(String status, String moderationStatus);
    List<PostMedia> findByStatusAndModerationStatusAndOwnerIdOrderByCreatedAtDesc(
            String status,
            String moderationStatus,
            String ownerId
    );
    List<PostMedia> findByStatusAndModerationStatusOrderByCreatedAtAsc(String status, String moderationStatus);
    List<PostMedia> findByPostIdAndStatusAndModerationStatusOrderByCreatedAtAsc(
            String postId,
            String status,
            String moderationStatus
    );
    List<PostMedia> findByPostIdAndStatusAndModerationStatusAndOwnerIdOrderByCreatedAtAsc(
            String postId,
            String status,
            String moderationStatus,
            String ownerId
    );
    List<PostMedia> findByPostIdAndCoverTrue(String postId);
    Optional<PostMedia> findByIdAndPostId(String id, String postId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attachment from PostMedia attachment where attachment.id = :id")
    Optional<PostMedia> findByIdForUpdate(@Param("id") String id);
    long countByPostId(String postId);
}
