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
            String captchaToken
    ) {}

    public record LoginInput(@NotBlank @Email String email, @NotBlank String password) {}
    public record TokenInput(@NotBlank String token) {}
    public record EmailInput(@NotBlank @Email String email) {}
    public record VerificationRequestInput(
            @NotBlank @Size(max = 2048) String profileLink,
            @NotBlank @Size(max = 1000) String explanation
    ) {}
    public record VerificationRejectionInput(@NotBlank @Size(max = 1000) String reason) {}
    public record AdminAssignment(String username, String role, boolean changed) {}
    public record AdminVerificationRequest(
            String id, String username, String displayName, String accountType,
            String profileLink, String explanation, String requestedAt
    ) {}
    public record VerificationDecision(String id, String username, String status) {}
    public record UserSummary(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerified) {}
    public record User(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerified, boolean isAuthenticated, String role, String verificationStatus, String verificationReviewReason) {}
    public record Profile(String id, String username, String displayName, String avatarUrl, String accountType, boolean isVerified, String bio, long followersCount, long followingCount, long postsCount, boolean isFollowedByCurrentUser) {}
    public record Principal(
            String id,
            String username,
            String displayName,
            String avatarUrl,
            String accountType,
            boolean isVerified,
            String role
    ) {}
    public record AuthenticatedUser(String token, User user) {}
}
