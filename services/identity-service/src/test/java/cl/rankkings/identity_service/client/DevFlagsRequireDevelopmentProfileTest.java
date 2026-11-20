package cl.rankkings.identity_service.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "rankkings.dev-captcha-bypass=true",
        "rankkings.dev-verification-logging=true",
        "rankkings.turnstile-secret=test-secret",
        "rankkings.verify-url=http://localhost:5173/verify-email"
})
class DevFlagsRequireDevelopmentProfileTest {
    @Autowired
    private CaptchaClient captchaClient;

    @Autowired
    private VerificationEmailClient verificationEmailClient;

    @Test
    void devFlagsDoNotBypassProviderConfigurationWithoutAnActiveDevelopmentProfile() {
        ResponseStatusException captchaFailure = assertThrows(
                ResponseStatusException.class,
                () -> captchaClient.verify("")
        );
        assertEquals(HttpStatus.BAD_REQUEST.value(), captchaFailure.getStatusCode().value());

        ResponseStatusException emailFailure = assertThrows(
                ResponseStatusException.class,
                () -> verificationEmailClient.sendVerification("test@example.com", "dummy-token")
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), emailFailure.getStatusCode().value());
    }
}
