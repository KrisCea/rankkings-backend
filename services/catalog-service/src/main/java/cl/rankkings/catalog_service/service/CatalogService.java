package cl.rankkings.catalog_service.service;

import cl.rankkings.catalog_service.dto.CatalogDtos.RankableItemDto;
import cl.rankkings.catalog_service.dto.CatalogDtos.CreateRankableItemInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.RatingStatsUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.SavedItemDto;
import cl.rankkings.catalog_service.model.RankableItem;
import cl.rankkings.catalog_service.model.SavedItem;
import cl.rankkings.catalog_service.repository.ItemRepository;
import cl.rankkings.catalog_service.repository.SavedItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ItemRepository itemRepository;
    private final SavedItemRepository savedItemRepository;

    @Transactional
    public RankableItemDto createItem(CreateRankableItemInput input) {
        validateCategoryDetails(input.category(), input.details());
        RankableItem.CreatorEmbed creator = new RankableItem.CreatorEmbed(
                input.creator().id() == null || input.creator().id().isBlank()
                        ? UUID.randomUUID().toString()
                        : input.creator().id(),
                input.creator().name().trim(),
                input.creator().avatarUrl(),
                input.creator().linkedUserId(),
                input.creator().linkedUsername()
        );
        List<RankableItem.ExternalLinkEmbed> externalLinks = input.externalLinks() == null
                ? List.of()
                : input.externalLinks().stream()
                        .map(link -> new RankableItem.ExternalLinkEmbed(
                                UUID.randomUUID().toString(), link.label(), link.url(), link.type()))
                        .toList();

        RankableItem item = RankableItem.builder()
                .id(UUID.randomUUID().toString())
                .category(input.category())
                .title(input.title().trim())
                .description(input.description())
                .creator(creator)
                .uploadedBy(input.uploadedBy())
                .year(input.year())
                .coverUrl(input.coverUrl() == null ? "" : input.coverUrl())
                .identifiers(input.identifiers() == null ? Map.of() : input.identifiers())
                .details(input.details() == null ? Map.of() : input.details())
                .verificationStatus("unverified")
                .postId(input.postId())
                .externalLinks(externalLinks)
                .build();
        return toDto(itemRepository.save(item), false);
    }

    @Transactional(readOnly = true)
    public RankableItemDto getItemById(String itemId, String currentUserId) {
        RankableItem item = findEntityOrThrow(itemId);
        boolean saved = currentUserId != null && savedItemRepository.existsByUserIdAndItemId(currentUserId, itemId);
        return toDto(item, saved);
    }

    @Transactional(readOnly = true)
    public List<RankableItemDto> searchItems(String query, String currentUserId) {
        List<RankableItem> items = itemRepository.findAllByTitleContainingIgnoreCaseOrderByTitleAsc(
                query == null ? "" : query.trim());
        if (items.isEmpty()) {
            return List.of();
        }

        Set<String> savedIds = currentUserId == null
                ? Set.of()
                : savedItemRepository.findSavedItemIdsByUser(
                        currentUserId, items.stream().map(RankableItem::getId).toList());

        return items.stream()
                .map(item -> toDto(item, savedIds.contains(item.getId())))
                .toList();
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
                entity.getDetails() == null ? Map.of() : entity.getDetails(),
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

    private void validateCategoryDetails(String category, Map<String, String> details) {
        Map<String, List<String>> requiredFields = Map.of(
                "music", List.of("format"),
                "podcasts", List.of("format"),
                "books", List.of("format"),
                "movies", List.of("format"),
                "concerts", List.of("eventDate", "venue", "city"),
                "videogames", List.of("platform"),
                "art", List.of("medium"),
                "boardgames", List.of("minPlayers", "maxPlayers", "playingTimeMinutes")
        );
        List<String> required = requiredFields.get(category);
        if (required == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category inválida");
        }
        Map<String, String> supplied = details == null ? Map.of() : details;
        List<String> missing = required.stream()
                .filter(field -> supplied.get(field) == null || supplied.get(field).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Faltan datos para " + category + ": " + String.join(", ", missing));
        }
    }
}
