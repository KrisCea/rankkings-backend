package cl.rankkings.catalog_service.controller;

import cl.rankkings.catalog_service.dto.CatalogDtos.RankableItemDto;
import cl.rankkings.catalog_service.dto.CatalogDtos.RatingStatsUpdateInput;
import cl.rankkings.catalog_service.dto.CatalogDtos.SavedItemDto;
import cl.rankkings.catalog_service.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/items/{itemId}")
    public ResponseEntity<RankableItemDto> getItem(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return ResponseEntity.ok(catalogService.getItemById(itemId, currentUserId));
    }

    @GetMapping("/items/{itemId}/children")
    public ResponseEntity<List<RankableItemDto>> getItemChildren(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return ResponseEntity.ok(catalogService.getChildren(itemId, currentUserId));
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

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión requerida");
        }
    }
}
