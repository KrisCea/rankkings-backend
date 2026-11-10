package cl.rankkings.catalog_service.repository;

import cl.rankkings.catalog_service.model.RankableItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<RankableItem, String> {

    List<RankableItem> findByParentIdOrderByPositionAsc(String parentId);

    List<RankableItem> findAllByTitleContainingIgnoreCaseOrderByTitleAsc(String title);

    @Query("SELECT AVG(i.averageRating) FROM RankableItem i WHERE i.parentId = :parentId AND i.ratingsCount > 0")
    Double calculateChildrenAverageRating(@Param("parentId") String parentId);
}
