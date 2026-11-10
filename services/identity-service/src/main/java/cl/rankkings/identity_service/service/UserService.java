package cl.rankkings.identity_service.service;

import cl.rankkings.identity_service.client.SocialPostsClient;
import cl.rankkings.identity_service.dto.IdentityDtos.Principal;
import cl.rankkings.identity_service.dto.IdentityDtos.Profile;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.dto.IdentityDtos.UserSummary;
import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.model.FollowRelation;
import cl.rankkings.identity_service.repository.AccountRepository;
import cl.rankkings.identity_service.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final AccountRepository accounts;
    private final FollowRepository follows;
    private final SessionService sessions;
    private final SocialPostsClient socialPostsClient;

    @Transactional(readOnly = true)
    public User currentUser(String cookie) {
        return toUser(sessions.requireAccount(cookie));
    }

    @Transactional(readOnly = true)
    public java.util.List<UserSummary> shareContacts(String cookie) {
        Account current = sessions.requireAccount(cookie);
        return follows.findByFollowerId(current.getId()).stream()
                .map(relation -> accounts.findById(relation.getFollowedId()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Profile profile(String username, String cookie) {
        Account target = findByUsername(username);
        return toProfile(target, sessions.findAccount(cookie));
    }

    @Transactional
    public Profile toggleFollow(String username, String cookie) {
        Account current = sessions.requireAccount(cookie);
        Account target = findByUsername(username);
        if (current.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes seguirte a ti mismo");
        }

        follows.findByFollowerIdAndFollowedId(current.getId(), target.getId())
                .ifPresentOrElse(
                        follows::delete,
                        () -> {
                            FollowRelation relation = new FollowRelation();
                            relation.setId(UUID.randomUUID().toString());
                            relation.setFollowerId(current.getId());
                            relation.setFollowedId(target.getId());
                            follows.save(relation);
                        }
                );
        return toProfile(target, current);
    }

    @Transactional(readOnly = true)
    public Principal introspect(String key, String cookie) {
        sessions.requireInternal(key);
        return toPrincipal(sessions.requireAccount(cookie));
    }

    @Transactional(readOnly = true)
    public Principal internalByUsername(String key, String username) {
        sessions.requireInternal(key);
        return toPrincipal(findByUsername(username));
    }

    @Transactional(readOnly = true)
    public Principal internalById(String key, String id) {
        sessions.requireInternal(key);
        Account account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toPrincipal(account);
    }

    private Account findByUsername(String username) {
        return accounts.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private Profile toProfile(Account account, Account viewer) {
        return new Profile(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                avatar(account),
                account.getAccountType(),
                false,
                account.getBio(),
                follows.countByFollowedId(account.getId()),
                follows.countByFollowerId(account.getId()),
                socialPostsClient.countByAuthor(account.getId()),
                viewer != null && follows.existsByFollowerIdAndFollowedId(viewer.getId(), account.getId())
        );
    }

    private User toUser(Account account) {
        return new User(account.getId(), account.getUsername(), account.getDisplayName(), avatar(account),
                account.getAccountType(), false, true, account.getRole());
    }

    private UserSummary toSummary(Account account) {
        return new UserSummary(account.getId(), account.getUsername(), account.getDisplayName(), avatar(account),
                account.getAccountType(), false);
    }

    private Principal toPrincipal(Account account) {
        return new Principal(account.getId(), account.getUsername(), account.getDisplayName(), avatar(account),
                account.getAccountType(), false);
    }

    private String avatar(Account account) {
        return account.getAvatarUrl() != null
                ? account.getAvatarUrl()
                : "https://api.dicebear.com/9.x/identicon/svg?seed=" + account.getId();
    }
}
