package cl.rankkings.identity_service.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;
    @Value("${rankkings.dev-verification-logging:false}") private boolean devVerificationLogging;
    @Value("${rankkings.resend-api-key:}") private String resendApiKey;
    @Value("${rankkings.email-from:}") private String emailFrom;
    @Value("${rankkings.verify-url:}") private String verifyUrl;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public void sendVerification(String email, String token) {
        if (verifyUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Configura VERIFICATION_URL para completar la verificación de correo"
            );
        }
        String separator = verifyUrl.contains("?") ? "&" : "?";
        String link = verifyUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        if (devVerificationLogging
                && environment.acceptsProfiles(Profiles.of("local"))
                && !environment.acceptsProfiles(Profiles.of("prod"))) {
            System.out.println("[LOCAL ONLY] Verificar cuenta " + email + ": " + link);
            return;
        }
        if (resendApiKey.isBlank() || emailFrom.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Configura RESEND_API_KEY y VERIFICATION_FROM_EMAIL para enviar el correo"
            );
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
