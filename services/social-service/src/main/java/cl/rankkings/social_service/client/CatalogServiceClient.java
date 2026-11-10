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
import java.util.Map;

@Component
public class CatalogServiceClient {
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @Value("${rankkings.catalog-url:http://localhost:8082}") private String catalogUrl;
    @Value("${rankkings.internal-key:}") private String internalKey;

    public CatalogServiceClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode getItem(String itemId, String viewerId) {
        String encodedId = encode(itemId);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(catalogUrl + "/items/" + encodedId))
                .timeout(Duration.ofSeconds(3))
                .header("X-Internal-Key", internalKey)
                .GET();
        if (viewerId != null && !viewerId.isBlank()) {
            request.header("X-User-Id", viewerId);
        }
        return get(request.build());
    }

    public void updateRatingStats(String itemId, double averageRating, int ratingsCount) {
        try {
            String body = mapper.writeValueAsString(Map.of("averageRating", averageRating, "ratingsCount", ratingsCount));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            catalogUrl + "/internal/items/" + encode(itemId) + "/rating-stats"))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Internal-Key", internalKey)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 204) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo actualizar el catálogo");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Actualización interrumpida");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible");
        }
    }

    private JsonNode get(HttpRequest request) {
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
