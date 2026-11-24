package cl.rankkings.identity_service.service;

import cl.rankkings.identity_service.client.SocialPostsClient;
import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.repository.AccountRepository;
import cl.rankkings.identity_service.repository.FollowRepository;
import cl.rankkings.identity_service.dto.IdentityDtos.VerificationRequestInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceAdminTest {
    private AccountRepository accounts;
    private SessionService sessions;
    private UserService userService;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        sessions = mock(SessionService.class);
        userService = new UserService(
                accounts,
                mock(FollowRepository.class),
                sessions,
                mock(SocialPostsClient.class)
        );
    }

    @Test
    void administratorCanPromoteVerifiedAccount() {
        Account administrator = account("admin-1", "admin", true);
        Account target = account("user-1", "user", true);
        when(accounts.findById("admin-1")).thenReturn(Optional.of(administrator));
        when(accounts.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(target));
        when(accounts.save(target)).thenReturn(target);

        var result = userService.promoteToAdmin("person@example.com", "admin-1");

        assertEquals("admin", target.getRole());
        assertTrue(result.changed());
        assertEquals("person", result.username());
        assertEquals("admin", result.role());
        verify(accounts).save(target);
    }

    @Test
    void nonAdministratorCannotPromoteAccount() {
        when(accounts.findById("user-1")).thenReturn(Optional.of(account("user-1", "user", true)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.promoteToAdmin("person@example.com", "user-1")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(accounts, never()).findByEmailIgnoreCase("person@example.com");
    }

    @Test
    void unverifiedAccountCannotBePromoted() {
        when(accounts.findById("admin-1")).thenReturn(Optional.of(account("admin-1", "admin", true)));
        when(accounts.findByEmailIgnoreCase("person@example.com"))
                .thenReturn(Optional.of(account("user-1", "user", false)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.promoteToAdmin("person@example.com", "admin-1")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertFalse(exception.getMessage().isBlank());
        verify(accounts, never()).save(org.mockito.ArgumentMatchers.any(Account.class));
    }

    @Test
    void verificationRequestIsSeparateFromEmailVerification() {
        Account account = account("user-1", "user", true);
        when(sessions.requireAccount("session-token")).thenReturn(account);
        when(accounts.save(account)).thenReturn(account);

        var result = userService.requestVerification(
                new VerificationRequestInput("https://example.com/profile", "Soy artista"), "session-token");

        assertEquals("pending", account.getVerificationStatus());
        assertEquals("https://example.com/profile", account.getVerificationLink());
        assertEquals("Soy artista", account.getVerificationExplanation());
        assertEquals("pending", result.verificationStatus());
        assertFalse(result.isVerified());
        assertTrue(account.isEmailVerified());
        verify(accounts).save(account);
    }

    @Test
    void rejectsNonHttpsVerificationLinks() {
        Account account = account("user-1", "user", true);
        when(sessions.requireAccount("session-token")).thenReturn(account);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.requestVerification(
                        new VerificationRequestInput("http://example.com/profile", "Soy artista"), "session-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(accounts, never()).save(org.mockito.ArgumentMatchers.any(Account.class));
    }

    @Test
    void administratorCanApproveOnlyPendingVerificationRequests() {
        Account administrator = account("admin-1", "admin", true);
        Account target = account("user-1", "user", true);
        target.setVerificationStatus("pending");
        when(accounts.findById("admin-1")).thenReturn(Optional.of(administrator));
        when(accounts.findByIdForUpdate("user-1")).thenReturn(Optional.of(target));
        when(accounts.save(target)).thenReturn(target);

        var decision = userService.approveVerification("user-1", "admin-1");

        assertEquals("verified", target.getVerificationStatus());
        assertEquals("verified", decision.status());
        assertEquals("admin-1", target.getVerificationReviewedBy());
        verify(accounts).save(target);
    }

    private Account account(String id, String role, boolean verified) {
        Account account = new Account();
        account.setId(id);
        account.setUsername(id.equals("user-1") ? "person" : "administrator");
        account.setEmail("person@example.com");
        account.setRole(role);
        account.setEmailVerified(verified);
        return account;
    }
}
