package cl.rankkings.identity_service.service;

import cl.rankkings.identity_service.Credentials;
import cl.rankkings.identity_service.client.CaptchaClient;
import cl.rankkings.identity_service.client.VerificationEmailClient;
import cl.rankkings.identity_service.dto.IdentityDtos.AuthenticatedUser;
import cl.rankkings.identity_service.dto.IdentityDtos.LoginInput;
import cl.rankkings.identity_service.dto.IdentityDtos.RegisterInput;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.model.Account;
import cl.rankkings.identity_service.model.EmailVerification;
import cl.rankkings.identity_service.repository.AccountRepository;
import cl.rankkings.identity_service.repository.VerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccountRepository accounts;
    private final VerificationRepository verifications;
    private final CaptchaClient captchaClient;
    private final VerificationEmailClient verificationEmailClient;
    private final SessionService sessionService;

    @Transactional
    public String register(RegisterInput input) {
        captchaClient.verify(input.captchaToken());
        String username = input.username().trim().toLowerCase(Locale.ROOT);
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByUsernameIgnoreCase(username) || accounts.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuario o correo ya registrado");
        }

        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUsername(username);
        account.setDisplayName(input.displayName().trim());
        account.setEmail(email);
        account.setPasswordHash(Credentials.hashPassword(input.password()));
        accounts.save(account);
        issueVerification(account);
        return account.getEmail();
    }

    @Transactional
    public AuthenticatedUser login(LoginInput input) {
        Account account = accounts.findByEmailIgnoreCase(input.email().trim()).orElse(null);
        if (account == null || !Credentials.checkPassword(input.password(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        if (!account.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifica tu correo antes de iniciar sesión");
        }
        return sessionService.create(account, user(account));
    }

    @Transactional
    public void verify(String token) {
        EmailVerification verification = verifications.findById(Credentials.sha256(token)).orElse(null);
        if (verification == null || !verification.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token inválido o expirado");
        }
        Account account = accounts.findById(verification.getAccountId()).orElseThrow();
        account.setEmailVerified(true);
        accounts.save(account);
        verifications.delete(verification);
    }

    @Transactional
    public void resendVerification(String email) {
        accounts.findByEmailIgnoreCase(email.trim())
                .filter(account -> !account.isEmailVerified())
                .ifPresent(this::issueVerification);
    }

    private void issueVerification(Account account) {
        verifications.deleteAll(verifications.findByAccountId(account.getId()));
        String rawToken = Credentials.randomToken();
        EmailVerification verification = new EmailVerification();
        verification.setTokenHash(Credentials.sha256(rawToken));
        verification.setAccountId(account.getId());
        verification.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        verifications.save(verification);
        verificationEmailClient.sendVerification(account.getEmail(), rawToken);
    }

    private User user(Account account) {
        return new User(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                avatar(account),
                account.getAccountType(),
                false,
                true,
                account.getRole()
        );
    }

    private String avatar(Account account) {
        return account.getAvatarUrl() != null
                ? account.getAvatarUrl()
                : "https://api.dicebear.com/9.x/identicon/svg?seed=" + account.getId();
    }
}
