package cl.rankkings.social_service.repository;

import cl.rankkings.social_service.model.PostComment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<PostComment, String> {
    List<PostComment> findByPostIdOrderByCreatedAtAsc(String postId);
    List<PostComment> findByPostIdAndKindAndDeletedAtIsNullOrderByCreatedAtDesc(String postId, String kind);
    List<PostComment> findByAuthorIdAndKindAndDeletedAtIsNullOrderByCreatedAtDesc(String authorId, String kind);
    long countByPostIdAndDeletedAtIsNull(String postId);
    Optional<PostComment> findByIdAndDeletedAtIsNull(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select comment from PostComment comment where comment.id = :id and comment.deletedAt is null")
    Optional<PostComment> findByIdAndDeletedAtIsNullForUpdate(@Param("id") String id);
    Optional<PostComment> findFirstByPostIdAndAuthorIdAndKindAndModerationStatusAndDeletedAtIsNullAndAuthorScoreIsNotNullOrderByCreatedAtDesc(
            String postId,
            String authorId,
            String kind,
            String moderationStatus
    );
    List<PostComment> findByKindAndModerationStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            String kind,
            String moderationStatus
    );
    List<PostComment> findByKindAndModerationStatusOrderByCreatedAtDesc(String kind, String moderationStatus);
    List<PostComment> findByPostIdAndModerationStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            String postId,
            String moderationStatus
    );
    long countByPostIdAndModerationStatusAndDeletedAtIsNull(String postId, String moderationStatus);
}
