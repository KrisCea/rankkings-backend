package cl.rankkings.identity_service.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VerificationEmailClient {
    private final ObjectMapper mapper;
    @Value("${rankkings.dev-verification-logging:false}") private boolean devVerificationLogging;
    @Value("${spring.profiles.active:local}") private String activeProfile;
    @Value("${rankkings.resend-api-key:}") private String resendApiKey;
    @Value("${rankkings.email-from:}") private String emailFrom;
    @Value("${rankkings.verify-url:http://localhost:5173/verify-email}") private String verifyUrl;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public void sendVerification(String email, String token) {
        String link = verifyUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        if (devVerificationLogging && activeProfile.equals("local")) {
            System.out.println("[LOCAL ONLY] Verificar cuenta " + email + ": " + link);
            return;
        }
        if (resendApiKey.isBlank() || emailFrom.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configura el servicio de verificación de correo");
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "from", emailFrom,
                    "to", List.of(email),
                    "subject", "Verifica tu correo en RankKings",
                    "text", "Abre este enlace para verificar tu correo: " + link
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el correo de verificación");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el correo");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el correo");
        }
    }
}
