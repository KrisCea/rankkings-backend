package cl.rankkings.catalog_service.repository;

import cl.rankkings.catalog_service.model.SavedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SavedItemRepository extends JpaRepository<SavedItem, String> {

    boolean existsByUserIdAndItemId(String userId, String itemId);

    Optional<SavedItem> findByUserIdAndItemId(String userId, String itemId);

    List<SavedItem> findByUserIdOrderBySavedAtDesc(String userId);

    @Query("SELECT s.itemId FROM SavedItem s WHERE s.userId = :userId AND s.itemId IN :itemIds")
    Set<String> findSavedItemIdsByUser(@Param("userId") String userId, @Param("itemIds") Collection<String> itemIds);

    int countByItemId(String itemId);
}
