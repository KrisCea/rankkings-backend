package cl.rankkings.identity_service.service;

import cl.rankkings.identity_service.client.SocialPostsClient;
import cl.rankkings.identity_service.dto.IdentityDtos.AdminAssignment;
import cl.rankkings.identity_service.dto.IdentityDtos.AdminVerificationRequest;
import cl.rankkings.identity_service.dto.IdentityDtos.Principal;
import cl.rankkings.identity_service.dto.IdentityDtos.Profile;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.dto.IdentityDtos.UserSummary;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationDecision;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationRejectionInput;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationRequestInput;
import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.model.FollowRelation;
import cl.rankkings.identity_service.repository.AccountRepository;
import cl.rankkings.identity_service.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
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

    @Transactional
    public AdminAssignment promoteToAdmin(String email, String viewerId) {
        requireAdministrator(viewerId);
        Account target = accounts.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        if (!target.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta debe verificar su correo antes de ser administradora");
        }

        boolean changed = !"admin".equals(target.getRole());
        if (changed) {
            target.setRole("admin");
            accounts.save(target);
        }
        return new AdminAssignment(target.getUsername(), target.getRole(), changed);
    }

    @Transactional
    public User requestVerification(VerificationRequestInput input, String cookie) {
        Account account = sessions.requireAccount(cookie);
        if ("verified".equals(account.getVerificationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta ya está verificada");
        }
        if ("pending".equals(account.getVerificationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya está en revisión");
        }

        account.setVerificationStatus("pending");
        account.setVerificationLink(requireHttpsLink(input.profileLink()));
        account.setVerificationExplanation(input.explanation().trim());
        account.setVerificationRequestedAt(Instant.now());
        account.setVerificationReviewedAt(null);
        account.setVerificationReviewedBy(null);
        account.setVerificationReviewReason(null);
        return toUser(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public List<AdminVerificationRequest> pendingVerificationRequests(String viewerId) {
        requireAdministrator(viewerId);
        return accounts.findByVerificationStatusOrderByVerificationRequestedAtDesc("pending").stream()
                .map(account -> new AdminVerificationRequest(
                        account.getId(),
                        account.getUsername(),
                        account.getDisplayName(),
                        account.getAccountType(),
                        account.getVerificationLink(),
                        account.getVerificationExplanation(),
                        account.getVerificationRequestedAt().toString()
                ))
                .toList();
    }

    @Transactional
    public VerificationDecision approveVerification(String targetId, String viewerId) {
        Account administrator = requireAdministrator(viewerId);
        Account target = requirePendingVerification(targetId);
        target.setVerificationStatus("verified");
        target.setVerificationReviewedAt(Instant.now());
        target.setVerificationReviewedBy(administrator.getId());
        target.setVerificationReviewReason(null);
        accounts.save(target);
        return new VerificationDecision(target.getId(), target.getUsername(), target.getVerificationStatus());
    }

    @Transactional
    public VerificationDecision rejectVerification(
            String targetId,
            VerificationRejectionInput input,
            String viewerId
    ) {
        Account administrator = requireAdministrator(viewerId);
        Account target = requirePendingVerification(targetId);
        target.setVerificationStatus("rejected");
        target.setVerificationReviewedAt(Instant.now());
        target.setVerificationReviewedBy(administrator.getId());
        target.setVerificationReviewReason(input.reason().trim());
        accounts.save(target);
        return new VerificationDecision(target.getId(), target.getUsername(), target.getVerificationStatus());
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

    private Account requireAdministrator(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión con una cuenta administradora");
        }
        Account administrator = accounts.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        if (!"admin".equals(administrator.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede asignar este rol");
        }
        return administrator;
    }

    private Account requirePendingVerification(String targetId) {
        Account target = accounts.findByIdForUpdate(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        if (!"pending".equals(target.getVerificationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue revisada");
        }
        return target;
    }

    private String requireHttpsLink(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace debe ser una URL HTTPS válida");
        }
    }

    private boolean isVerified(Account account) {
        return "verified".equals(account.getVerificationStatus());
    }

    private Profile toProfile(Account account, Account viewer) {
        return new Profile(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                avatar(account),
                account.getAccountType(),
                isVerified(account),
                account.getBio(),
                follows.countByFollowedId(account.getId()),
                follows.countByFollowerId(account.getId()),
                socialPostsClient.countByAuthor(account.getId()),
                viewer != null && follows.existsByFollowerIdAndFollowedId(viewer.getId(), account.getId())
        );
    }

    private User toUser(Account account) {
        return new User(account.getId(), account.getUsername(), account.getDisplayName(), avatar(account),
                account.getAccountType(), isVerified(account), true, account.getRole(),
                account.getVerificationStatus(), account.getVerificationReviewReason());
    }

    private UserSummary toSummary(Account account) {
        return new UserSummary(account.getId(), account.getUsername(), account.getDisplayName(), avatar(account),
                account.getAccountType(), isVerified(account));
    }

    private Principal toPrincipal(Account account) {
        return new Principal(account.getId(), account.getUsername(), account.getDisplayName(), avatar(account),
                account.getAccountType(), isVerified(account), account.getRole());
    }

    private String avatar(Account account) {
        return account.getAvatarUrl() != null
                ? account.getAvatarUrl()
                : "https://api.dicebear.com/9.x/identicon/svg?seed=" + account.getId();
    }
}
