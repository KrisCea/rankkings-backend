package cl.rankkings.identity_service.controller;

import cl.rankkings.identity_service.dto.IdentityDtos.Principal;
import cl.rankkings.identity_service.dto.IdentityDtos.Profile;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.dto.IdentityDtos.UserSummary;
import cl.rankkings.identity_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
