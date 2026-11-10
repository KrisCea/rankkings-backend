package cl.rankkings.catalog_service.dto;

import cl.rankkings.catalog_service.model.RankableItem;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class CatalogDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RankableItemDto(
            String id,
            String category,
            String title,
            String description,
            RankableItem.CreatorEmbed creator,
            String uploadedBy,
            Integer year,
            String coverUrl,
            Map<String, String> identifiers,
            String verificationStatus,
            String postId,
            double averageRating,
            int ratingsCount,
            String parentId,
            Integer position,
            int childrenCount,
            Double childrenAverageRating,
            boolean savedByCurrentUser,
            int savesCount,
            List<RankableItem.ExternalLinkEmbed> externalLinks,
            String createdAt
    ) {}

    public record SavedItemDto(
            RankableItemDto item,
            String savedAt
    ) {}

    public record RatingStatsUpdateInput(
            double averageRating,
            int ratingsCount
    ) {}
}
