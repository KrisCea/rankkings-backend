package cl.rankkings.identity_service.controller;

import cl.rankkings.identity_service.dto.IdentityDtos.Principal;
import cl.rankkings.identity_service.dto.IdentityDtos.Profile;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.dto.IdentityDtos.AdminAssignment;
import cl.rankkings.identity_service.dto.IdentityDtos.AdminVerificationRequest;
import cl.rankkings.identity_service.dto.IdentityDtos.EmailInput;
import cl.rankkings.identity_service.dto.IdentityDtos.UserSummary;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationDecision;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationRejectionInput;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationRequestInput;
import cl.rankkings.identity_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/users/me")
    public User me(@CookieValue(value = "rk_token", required = false) String token) {
        return userService.currentUser(token);
    }

    @GetMapping("/users/me/share-contacts")
    public List<UserSummary> contacts(@CookieValue(value = "rk_token", required = false) String token) {
        return userService.shareContacts(token);
    }

    @GetMapping("/users/{username}")
    public Profile profile(
            @PathVariable String username,
            @CookieValue(value = "rk_token", required = false) String token
    ) {
        return userService.profile(username, token);
    }

    @PostMapping("/users/{username}/follow")
    public Profile follow(
            @PathVariable String username,
            @CookieValue(value = "rk_token", required = false) String token
    ) {
        return userService.toggleFollow(username, token);
    }

    @PostMapping("/admin/users/promote")
    public AdminAssignment promoteToAdmin(
            @Valid @RequestBody EmailInput input,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return userService.promoteToAdmin(input.email(), viewerId);
    }

    @PostMapping("/users/me/verification-request")
    public User requestVerification(
            @Valid @RequestBody VerificationRequestInput input,
            @CookieValue(value = "rk_token", required = false) String token
    ) {
        return userService.requestVerification(input, token);
    }

    @GetMapping("/admin/users/verification-requests")
    public List<AdminVerificationRequest> pendingVerificationRequests(
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return userService.pendingVerificationRequests(viewerId);
    }

    @PostMapping("/admin/users/{userId}/verification/approve")
    public VerificationDecision approveVerification(
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return userService.approveVerification(userId, viewerId);
    }

    @PostMapping("/admin/users/{userId}/verification/reject")
    public VerificationDecision rejectVerification(
            @PathVariable String userId,
            @Valid @RequestBody VerificationRejectionInput input,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return userService.rejectVerification(userId, input, viewerId);
    }

    @GetMapping("/internal/session")
    public Principal introspect(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @CookieValue(value = "rk_token", required = false) String token
    ) {
        return userService.introspect(key, token);
    }

    @GetMapping("/internal/usernames/{username}")
    public Principal internalByUsername(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @PathVariable String username
    ) {
        return userService.internalByUsername(key, username);
    }

    @GetMapping("/internal/users/{id}")
    public Principal internalById(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @PathVariable String id
    ) {
        return userService.internalById(key, id);
    }
}
