package cl.rankkings.social_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SocialDtos {
    private SocialDtos() {}

    public record NewPostInput(
            @NotBlank String itemId,
            @NotBlank String kind,
            @NotBlank @Size(max = 2000) String caption,
            Double authorScore
    ) {}

    public record RatingInput(@NotNull @DecimalMin("1.0") @DecimalMax("10.0") Double score) {}
    public record ShareInput(@NotBlank String recipientId) {}
    public record NewCommentInput(@NotBlank @Size(max = 2000) String text, String parentId, String replyToUsername) {}
}
