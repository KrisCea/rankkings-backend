package cl.rankkings.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class IdentityDtos {
    private IdentityDtos() {}

    public record RegisterInput(
            @NotBlank String displayName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}") String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank String captchaToken
    ) {}

    public record LoginInput(@NotBlank @Email String email, @NotBlank String password) {}
    public record TokenInput(@NotBlank String token) {}
    public record EmailInput(@NotBlank @Email String email) {}
    public record UserSummary(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerifiedBusiness) {}
    public record User(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerifiedBusiness, boolean isAuthenticated, String role) {}
    public record Profile(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerifiedBusiness, String bio, long followersCount, long followingCount, long postsCount, boolean isFollowedByCurrentUser) {}
    public record Principal(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerifiedBusiness) {}
    public record AuthenticatedUser(String token, User user) {}
}
