package cl.rankkings.social_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class SocialDtos {
    private SocialDtos() {}

    public record NewPostInput(
            String itemId,
            @Valid NewRankableItemInput rankableItem,
            @NotBlank String kind,
            @NotBlank @Size(max = 2000) String caption
    ) {}

    public record NewRankableItemInput(
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Valid NewCreatorInput creator,
            Integer year,
            @Size(max = 2048) String coverUrl,
            Map<String, String> identifiers,
            Map<String, String> details,
            List<@Valid NewExternalLinkInput> externalLinks,
            @Size(max = 200) String creatorName
    ) {
        public NewRankableItemInput(
                String category,
                String title,
                String description,
                NewCreatorInput creator,
                Integer year,
                String coverUrl,
                Map<String, String> identifiers,
                Map<String, String> details,
                List<NewExternalLinkInput> externalLinks
        ) {
            this(category, title, description, creator, year, coverUrl, identifiers, details, externalLinks, null);
        }

        public NewCreatorInput creatorInput() {
            return creator != null ? creator : new NewCreatorInput(null, creatorName, null, null, null);
        }
    }

    public record NewCreatorInput(
            String id,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2048) String avatarUrl,
            String linkedUserId,
            String linkedUsername
    ) {}

    public record NewExternalLinkInput(
            @NotBlank @Size(max = 100) String label,
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 20) String type
    ) {}

    public record NewReviewInput(
            @NotBlank @Size(max = 2000) String text,
            @DecimalMin("1.0") @DecimalMax("10.0") Double authorScore
    ) {}

    public record RatingInput(@NotNull @DecimalMin("1.0") @DecimalMax("10.0") Double score) {}
    public record ShareInput(@NotBlank String recipientId) {}
    public record NewCommentInput(@NotBlank @Size(max = 2000) String text, String parentId, String replyToUsername) {}
    public record ImageUploadInput(
            @NotBlank String contentType,
            @NotNull @Positive @Max(10_485_760) Long sizeBytes,
            Boolean isCover
    ) {
        public ImageUploadInput {
            isCover = Boolean.TRUE.equals(isCover);
        }
    }
    public record ExternalMediaInput(
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 20) String type
    ) {}
    public record ModerationRejectionInput(@NotBlank @Size(max = 1000) String reason) {}
}
