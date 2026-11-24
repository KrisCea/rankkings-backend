package cl.rankkings.catalog_service.service;

import cl.rankkings.catalog_service.dto.CatalogDtos.RankableItemDto;
import cl.rankkings.catalog_service.dto.CatalogDtos.CreateRankableItemInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.RatingStatsUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.ItemCoverUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.ItemModerationUpdateInput;
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
                .verificationStatus(input.uploaderVerified() ? "verified" : "unverified")
                .moderationStatus(input.uploaderVerified() ? "approved" : "pending")
                .postId(input.postId())
                .externalLinks(externalLinks)
                .build();
        return toDto(itemRepository.save(item), false);
    }

    @Transactional(readOnly = true)
    public RankableItemDto getItemById(String itemId, String currentUserId) {
        return getItemById(itemId, currentUserId, null);
    }

    @Transactional(readOnly = true)
    public RankableItemDto getItemById(String itemId, String currentUserId, String currentUserRole) {
        RankableItem item = findEntityOrThrow(itemId);
        requireVisible(item, currentUserId, currentUserRole);
        boolean saved = currentUserId != null && savedItemRepository.existsByUserIdAndItemId(currentUserId, itemId);
        return toDto(item, saved);
    }

    @Transactional(readOnly = true)
    public RankableItemDto getItemInternal(String itemId) {
        return toDto(findEntityOrThrow(itemId), false);
    }

    @Transactional(readOnly = true)
    public List<RankableItemDto> getItemsForModeration(String status) {
        return itemRepository.findByModerationStatusOrderByCreatedAtDesc(status).stream()
                .map(item -> toDto(item, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RankableItemDto> searchItems(String query, String currentUserId) {
        return searchItems(query, currentUserId, null);
    }

    @Transactional(readOnly = true)
    public List<RankableItemDto> searchItems(String query, String currentUserId, String currentUserRole) {
        List<RankableItem> items = itemRepository.findAllByTitleContainingIgnoreCaseOrderByTitleAsc(
                query == null ? "" : query.trim());
        items = items.stream()
                .filter(item -> canView(item, currentUserId, currentUserRole))
                .toList();
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
        return getChildren(parentId, currentUserId, null);
    }

    @Transactional(readOnly = true)
    public List<RankableItemDto> getChildren(String parentId, String currentUserId, String currentUserRole) {
        if (!itemRepository.existsById(parentId)) {
            throw new EntityNotFoundException("Obra padre no encontrada: " + parentId);
        }

        RankableItem parent = findEntityOrThrow(parentId);
        requireVisible(parent, currentUserId, currentUserRole);
        List<RankableItem> children = itemRepository.findByParentIdOrderByPositionAsc(parentId).stream()
                .filter(item -> canView(item, currentUserId, currentUserRole))
                .toList();
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
        if (!"approved".equals(item.getModerationStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
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
                .filter(saved -> canView(itemsMap.get(saved.getItemId()), userId, null))
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

    @Transactional
    public void updateItemCover(String itemId, ItemCoverUpdateInput input) {
        RankableItem item = findEntityOrThrow(itemId);
        if (!item.getPostId().equals(input.postId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La obra no pertenece a esta publicación");
        }
        item.setCoverUrl(input.coverUrl());
        itemRepository.save(item);
    }

    @Transactional
    public void updateItemModerationStatus(String itemId, ItemModerationUpdateInput input) {
        RankableItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Obra no encontrada: " + itemId));
        if (!item.getPostId().equals(input.postId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La obra no pertenece a esta publicación");
        }
        if (!List.of("pending", "approved", "rejected").contains(input.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de moderación inválido");
        }
        if (!"pending".equals(item.getModerationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El Rankable Item ya fue moderado");
        }
        item.setModerationStatus(input.status());
        item.setVerificationStatus("approved".equals(input.status()) ? "verified" : "unverified");
        item.setModeratedBy(input.moderatedBy());
        item.setModeratedAt(input.moderatedAt());
        item.setModerationReason(input.reason());
        itemRepository.save(item);
    }

    @Transactional
    public void deleteCreatedItemByPostId(String postId) {
        itemRepository.findFirstByPostId(postId).ifPresent(item -> {
            savedItemRepository.deleteByItemId(item.getId());
            itemRepository.delete(item);
        });
    }

    private RankableItem findEntityOrThrow(String itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Obra no encontrada: " + itemId));
    }

    private void requireVisible(RankableItem item, String currentUserId, String currentUserRole) {
        if (!canView(item, currentUserId, currentUserRole)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
    }

    private boolean canView(RankableItem item, String currentUserId, String currentUserRole) {
        return "approved".equals(item.getModerationStatus())
                || "admin".equals(currentUserRole)
                || (currentUserId != null && currentUserId.equals(item.getUploadedBy()));
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
                entity.getModerationStatus(),
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
        if (details == null || details.isEmpty()) {
            return;
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
