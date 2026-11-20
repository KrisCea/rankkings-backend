package cl.rankkings.identity_service.client;

import cl.rankkings.identity_service.dto.IdentityDtos.RegisterInput;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "rankkings.dev-captcha-bypass=true")
@ActiveProfiles("test")
class CaptchaBypassProfileTest {
    @Autowired
    private CaptchaClient captchaClient;

    @Autowired
    private Validator validator;

    @Test
    void allowsAnEmptyTokenWhenTheTestProfileBypassIsEnabled() {
        assertDoesNotThrow(() -> captchaClient.verify(""));
    }

    @Test
    void registerDtoDoesNotRejectCaptchaBeforeProfileSpecificVerification() {
        RegisterInput input = new RegisterInput(
                "Test User",
                "test_user",
                "test@example.com",
                "long-password",
                ""
        );

        assertTrue(validator.validate(input).stream()
                .noneMatch(violation -> violation.getPropertyPath().toString().equals("captchaToken")));
    }
}
