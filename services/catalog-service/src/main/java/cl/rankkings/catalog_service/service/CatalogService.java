package cl.rankkings.catalog_service.service;

import cl.rankkings.catalog_service.dto.CatalogDtos.RankableItemDto;
import cl.rankkings.catalog_service.dto.CatalogDtos.RatingStatsUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.SavedItemDto;
import cl.rankkings.catalog_service.model.RankableItem;
import cl.rankkings.catalog_service.model.SavedItem;
import cl.rankkings.catalog_service.repository.ItemRepository;
import cl.rankkings.catalog_service.repository.SavedItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ItemRepository itemRepository;
    private final SavedItemRepository savedItemRepository;

    @Transactional(readOnly = true)
    public RankableItemDto getItemById(String itemId, String currentUserId) {
        RankableItem item = findEntityOrThrow(itemId);
        boolean saved = currentUserId != null && savedItemRepository.existsByUserIdAndItemId(currentUserId, itemId);
        return toDto(item, saved);
    }

    @Transactional(readOnly = true)
    public List<RankableItemDto> getChildren(String parentId, String currentUserId) {
        if (!itemRepository.existsById(parentId)) {
            throw new EntityNotFoundException("Obra padre no encontrada: " + parentId);
        }

        List<RankableItem> children = itemRepository.findByParentIdOrderByPositionAsc(parentId);
        if (children.isEmpty()) {
            return List.of();
        }

        Set<String> savedIds = (currentUserId == null)
                ? Set.of()
                : savedItemRepository.findSavedItemIdsByUser(currentUserId, children.stream().map(RankableItem::getId).toList());

        return children.stream()
                .map(child -> toDto(child, savedIds.contains(child.getId())))
                .toList();
    }

    @Transactional
    public RankableItemDto saveItem(String itemId, String userId) {
        RankableItem item = findEntityOrThrow(itemId);
        if (!savedItemRepository.existsByUserIdAndItemId(userId, itemId)) {
            savedItemRepository.save(SavedItem.builder()
                    .userId(userId)
                    .itemId(itemId)
                    .build());
            item.setSavesCount(savedItemRepository.countByItemId(itemId));
            itemRepository.save(item);
        }
        return toDto(item, true);
    }

    @Transactional
    public RankableItemDto unsaveItem(String itemId, String userId) {
        RankableItem item = findEntityOrThrow(itemId);
        savedItemRepository.findByUserIdAndItemId(userId, itemId).ifPresent(existing -> {
            savedItemRepository.delete(existing);
            item.setSavesCount(savedItemRepository.countByItemId(itemId));
            itemRepository.save(item);
        });
        return toDto(item, false);
    }

    @Transactional(readOnly = true)
    public List<SavedItemDto> getSavedItemsByUser(String userId) {
        List<SavedItem> savedList = savedItemRepository.findByUserIdOrderBySavedAtDesc(userId);
        if (savedList.isEmpty()) {
            return List.of();
        }

        List<String> itemIds = savedList.stream().map(SavedItem::getItemId).toList();
        Map<String, RankableItem> itemsMap = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(RankableItem::getId, Function.identity()));

        return savedList.stream()
                .filter(saved -> itemsMap.containsKey(saved.getItemId()))
                .map(saved -> new SavedItemDto(
                        toDto(itemsMap.get(saved.getItemId()), true),
                        saved.getSavedAt().toString()
                ))
                .toList();
    }

    @Transactional
    public void updateItemRatingStats(String itemId, RatingStatsUpdateInput input) {
        RankableItem item = findEntityOrThrow(itemId);
        item.setAverageRating(input.averageRating());
        item.setRatingsCount(input.ratingsCount());
        itemRepository.save(item);

        if (item.getParentId() != null) {
            itemRepository.findById(item.getParentId()).ifPresent(parent -> {
                Double childrenAvg = itemRepository.calculateChildrenAverageRating(parent.getId());
                parent.setChildrenAverageRating(childrenAvg != null ? Math.round(childrenAvg * 10.0) / 10.0 : null);
                itemRepository.save(parent);
            });
        }
    }

    private RankableItem findEntityOrThrow(String itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Obra no encontrada: " + itemId));
    }

    private RankableItemDto toDto(RankableItem entity, boolean savedByCurrentUser) {
        return new RankableItemDto(
                entity.getId(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCreator(),
                entity.getUploadedBy(),
                entity.getYear(),
                entity.getCoverUrl(),
                entity.getIdentifiers(),
                entity.getVerificationStatus(),
                entity.getPostId(),
                entity.getAverageRating(),
                entity.getRatingsCount(),
                entity.getParentId(),
                entity.getPosition(),
                entity.getChildrenCount(),
                entity.getChildrenAverageRating(),
                savedByCurrentUser,
                entity.getSavesCount(),
                entity.getExternalLinks(),
                entity.getCreatedAt().toString()
        );
    }
}
