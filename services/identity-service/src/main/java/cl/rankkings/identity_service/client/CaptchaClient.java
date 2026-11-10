package cl.rankkings.identity_service.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CaptchaClient {
    private final ObjectMapper mapper;
    @Value("${rankkings.turnstile-secret:}") private String turnstileSecret;
    @Value("${rankkings.dev-captcha-bypass:false}") private boolean devCaptchaBypass;
    @Value("${spring.profiles.active:local}") private String activeProfile;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public void verify(String token) {
        if (devCaptchaBypass && (activeProfile.equals("local") || activeProfile.equals("test"))) {
            return;
        }
        if (turnstileSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Captcha no configurado");
        }

        String form = "secret=" + encode(turnstileSecret) + "&response=" + encode(token);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Captcha inválido");
            }
            JsonNode result = mapper.readTree(response.body());
            if (!result.path("success").asBoolean(false)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Captcha inválido");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Captcha temporalmente no disponible");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Captcha temporalmente no disponible");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
