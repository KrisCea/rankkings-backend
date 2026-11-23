package cl.rankkings.catalog_service.controller;

import cl.rankkings.catalog_service.dto.CatalogDtos.RankableItemDto;
import cl.rankkings.catalog_service.dto.CatalogDtos.CreateRankableItemInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.RatingStatsUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.ItemCoverUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.ItemModerationUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.SavedItemDto;
import cl.rankkings.catalog_service.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    @Value("${rankkings.internal-key:}") private String internalKey;

    @GetMapping("/items")
    public ResponseEntity<List<RankableItemDto>> searchItems(
            @RequestParam(required = false) String query,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        return ResponseEntity.ok(catalogService.searchItems(query, currentUserId, currentUserRole));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<RankableItemDto> getItem(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        return ResponseEntity.ok(catalogService.getItemById(itemId, currentUserId, currentUserRole));
    }

    @GetMapping("/items/{itemId}/children")
    public ResponseEntity<List<RankableItemDto>> getItemChildren(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        return ResponseEntity.ok(catalogService.getChildren(itemId, currentUserId, currentUserRole));
    }

    @PostMapping("/internal/items")
    public ResponseEntity<RankableItemDto> createItem(
            @Valid @RequestBody CreateRankableItemInput input,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        requireInternal(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createItem(input));
    }

    @GetMapping("/internal/items/{itemId}")
    public ResponseEntity<RankableItemDto> getItemInternal(
            @PathVariable String itemId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        requireInternal(key);
        return ResponseEntity.ok(catalogService.getItemInternal(itemId));
    }

    @PutMapping("/items/{itemId}/save")
    public ResponseEntity<RankableItemDto> saveItem(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        requireUser(currentUserId);
        return ResponseEntity.ok(catalogService.saveItem(itemId, currentUserId));
    }

    @DeleteMapping("/items/{itemId}/save")
    public ResponseEntity<RankableItemDto> unsaveItem(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        requireUser(currentUserId);
        return ResponseEntity.ok(catalogService.unsaveItem(itemId, currentUserId));
    }

    @GetMapping("/users/me/saved")
    public ResponseEntity<List<SavedItemDto>> getMySavedItems(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        requireUser(currentUserId);
        return ResponseEntity.ok(catalogService.getSavedItemsByUser(currentUserId));
    }

    @PutMapping("/internal/items/{itemId}/rating-stats")
    public ResponseEntity<Void> updateRatingStats(
            @PathVariable String itemId,
            @RequestBody RatingStatsUpdateInput input) {
        catalogService.updateItemRatingStats(itemId, input);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/internal/items/{itemId}/cover")
    public ResponseEntity<Void> updateItemCover(
            @PathVariable String itemId,
            @Valid @RequestBody ItemCoverUpdateInput input,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        requireInternal(key);
        catalogService.updateItemCover(itemId, input);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/internal/items/{itemId}/moderation")
    public ResponseEntity<Void> updateItemModeration(
            @PathVariable String itemId,
            @Valid @RequestBody ItemModerationUpdateInput input,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        requireInternal(key);
        catalogService.updateItemModerationStatus(itemId, input);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/internal/items/by-post/{postId}")
    public ResponseEntity<Void> deleteCreatedItem(
            @PathVariable String postId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        requireInternal(key);
        catalogService.deleteCreatedItemByPostId(postId);
        return ResponseEntity.noContent().build();
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión requerida");
        }
    }

    private void requireInternal(String key) {
        if (internalKey.isBlank() || !internalKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso interno requerido");
        }
    }
}
