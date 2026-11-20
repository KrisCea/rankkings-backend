package cl.rankkings.catalog_service.dto;

import cl.rankkings.catalog_service.model.RankableItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
            Map<String, String> details,
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

    public record CreateRankableItemInput(
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @Valid CreatorInput creator,
            Integer year,
            @Size(max = 2048) String coverUrl,
            Map<String, String> identifiers,
            Map<String, String> details,
            List<@Valid ExternalLinkInput> externalLinks,
            @NotBlank String uploadedBy,
            @NotBlank String postId
    ) {}

    public record CreatorInput(
            @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2048) String avatarUrl,
            String linkedUserId,
            String linkedUsername
    ) {}

    public record ExternalLinkInput(
            @NotBlank @Size(max = 100) String label,
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 20) String type
    ) {}

    public record RatingStatsUpdateInput(
            double averageRating,
            int ratingsCount
    ) {}
}
