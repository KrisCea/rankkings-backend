package cl.rankkings.catalog_service.repository;

import cl.rankkings.catalog_service.model.RankableItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<RankableItem, String> {

    List<RankableItem> findByParentIdOrderByPositionAsc(String parentId);
    List<RankableItem> findByModerationStatusOrderByCreatedAtDesc(String moderationStatus);

    List<RankableItem> findAllByTitleContainingIgnoreCaseOrderByTitleAsc(String title);
    List<RankableItem> findAllByTitleContainingIgnoreCaseAndModerationStatusOrderByTitleAsc(
            String title,
            String moderationStatus
    );

    List<RankableItem> findByParentIdAndModerationStatusOrderByPositionAsc(String parentId, String moderationStatus);

    Optional<RankableItem> findFirstByPostId(String postId);

    @Query("SELECT AVG(i.averageRating) FROM RankableItem i WHERE i.parentId = :parentId AND i.ratingsCount > 0")
    Double calculateChildrenAverageRating(@Param("parentId") String parentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT item FROM RankableItem item WHERE item.id = :id")
    Optional<RankableItem> findByIdForUpdate(@Param("id") String id);
}
