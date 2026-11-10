package cl.rankkings.social_service.client;

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
public class IdentityServiceClient {
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @Value("${rankkings.identity-url:http://localhost:8081}") private String identityUrl;
    @Value("${rankkings.internal-key:}") private String internalKey;

    public IdentityServiceClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode getUser(String userId) {
        return get("/internal/users/" + encode(userId));
    }

    public JsonNode findUserByUsername(String username) {
        return get("/internal/usernames/" + encode(username));
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(identityUrl + path))
                .timeout(Duration.ofSeconds(3))
                .header("X-Internal-Key", internalKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Servicio asociado devolvió un error");
            }
            return mapper.readTree(response.body());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio asociado interrumpido");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio asociado no disponible");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
