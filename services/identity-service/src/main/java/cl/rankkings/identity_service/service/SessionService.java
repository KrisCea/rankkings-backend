package cl.rankkings.identity_service.service;

import cl.rankkings.identity_service.Credentials;
import cl.rankkings.identity_service.dto.IdentityDtos.AuthenticatedUser;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.model.LoginSession;
import cl.rankkings.identity_service.repository.AccountRepository;
import cl.rankkings.identity_service.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

@Service
public class SessionService {
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final boolean cookieSecure;
    private final String internalKey;

    public SessionService(
            AccountRepository accounts,
            SessionRepository sessions,
            @Value("${rankkings.cookie-secure:false}") boolean cookieSecure,
            @Value("${rankkings.internal-key:}") String internalKey
    ) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cookieSecure = cookieSecure;
        this.internalKey = internalKey;
    }

    public Account findAccount(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        LoginSession session = sessions.findById(Credentials.sha256(cookie)).orElse(null);
        if (session == null || !session.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        return accounts.findById(session.getAccountId()).orElse(null);
    }

    public Account requireAccount(String cookie) {
        Account account = findAccount(cookie);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión requerida");
        }
        return account;
    }

    public AuthenticatedUser create(Account account, User user) {
        String rawToken = Credentials.randomToken();
        LoginSession session = new LoginSession();
        session.setTokenHash(Credentials.sha256(rawToken));
        session.setAccountId(account.getId());
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        sessions.save(session);
        return new AuthenticatedUser(rawToken, user);
    }

    public void delete(String cookie) {
        if (cookie != null) {
            sessions.deleteById(Credentials.sha256(cookie));
        }
    }

    public String cookie(String token, long maxAgeSeconds) {
        return "rk_token=" + token + "; Path=/; HttpOnly; SameSite=Lax"
                + (cookieSecure ? "; Secure" : "")
                + "; Max-Age=" + maxAgeSeconds;
    }

    public void requireInternal(String key) {
        if (internalKey.isBlank() || key == null
                || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), internalKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal request denied");
        }
    }
}
