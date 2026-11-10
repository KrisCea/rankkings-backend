package cl.rankkings.identity_service.controller;

import cl.rankkings.identity_service.dto.IdentityDtos.AuthenticatedUser;
import cl.rankkings.identity_service.dto.IdentityDtos.EmailInput;
import cl.rankkings.identity_service.dto.IdentityDtos.LoginInput;
import cl.rankkings.identity_service.dto.IdentityDtos.RegisterInput;
import cl.rankkings.identity_service.dto.IdentityDtos.TokenInput;
import cl.rankkings.identity_service.dto.IdentityDtos.User;
import cl.rankkings.identity_service.service.AuthService;
import cl.rankkings.identity_service.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private static final long SESSION_SECONDS = 7L * 24 * 60 * 60;
    private final AuthService authService;
    private final SessionService sessionService;

    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterInput input) {
        String email = authService.register(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("email", email));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<User> login(@Valid @RequestBody LoginInput input) {
        AuthenticatedUser authenticated = authService.login(input);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionService.cookie(authenticated.token(), SESSION_SECONDS))
                .body(authenticated.user());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "rk_token", required = false) String token) {
        sessionService.delete(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionService.cookie("", 0))
                .build();
    }

    @PostMapping("/auth/verify-email")
    public ResponseEntity<Void> verify(@Valid @RequestBody TokenInput input) {
        authService.verify(input.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/resend-verification")
    public ResponseEntity<Void> resend(@Valid @RequestBody EmailInput input) {
        authService.resendVerification(input.email());
        return ResponseEntity.noContent().build();
    }
}
