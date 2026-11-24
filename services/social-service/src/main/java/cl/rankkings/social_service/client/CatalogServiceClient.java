package cl.rankkings.social_service.client;

import cl.rankkings.social_service.dto.SocialDtos.NewRankableItemInput;
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
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

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
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(catalogUrl + "/internal/items/" + encodedId))
                .timeout(Duration.ofSeconds(3))
                .header("X-Internal-Key", internalKey)
                .GET();
        if (viewerId != null && !viewerId.isBlank()) {
            request.header("X-User-Id", viewerId);
        }
        return get(request.build());
    }

    public List<JsonNode> getModerationItems(String status) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        catalogUrl + "/internal/items?status=" + encode(status)))
                .timeout(Duration.ofSeconds(3))
                .header("X-Internal-Key", internalKey)
                .GET()
                .build();
        JsonNode response = get(request);
        java.util.ArrayList<JsonNode> items = new java.util.ArrayList<>();
        response.forEach(items::add);
        return List.copyOf(items);
    }

    public void updateItemModerationStatus(
            String itemId,
            String postId,
            String status,
            String moderatorId,
            String reason,
            Instant moderatedAt
    ) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("postId", postId);
            input.put("status", status);
            input.put("moderatedBy", moderatorId);
            input.put("reason", reason);
            input.put("moderatedAt", moderatedAt.toString());
            String body = mapper.writeValueAsString(input);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            catalogUrl + "/internal/items/" + encode(itemId) + "/moderation"))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Internal-Key", internalKey)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El Rankable Item ya fue moderado");
            }
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rankable Item no encontrado");
            }
            if (response.statusCode() != 204) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo actualizar la moderación del catálogo");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Actualización de moderación interrumpida");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible");
        }
    }

    public JsonNode createItem(NewRankableItemInput input, String authorId, String postId, boolean uploaderVerified) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("category", input.category());
        body.put("title", input.title());
        body.put("description", input.description());
        body.put("creator", input.creatorInput());
        body.put("uploadedBy", authorId);
        body.put("uploaderVerified", uploaderVerified);
        body.put("year", input.year());
        body.put("coverUrl", input.coverUrl());
        body.put("identifiers", input.identifiers());
        body.put("details", input.details());
        body.put("externalLinks", input.externalLinks());
        body.put("postId", postId);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(catalogUrl + "/internal/items"))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Internal-Key", internalKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400) {
                String message = mapper.readTree(response.body()).path("message")
                        .asString("Datos del Rankable Item inválidos");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
            }
            if (response.statusCode() != 201) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo crear el Rankable Item");
            }
            return mapper.readTree(response.body());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Creación del Rankable Item interrumpida");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible");
        }
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

    public void updateItemCover(String itemId, String postId, String coverUrl) {
        try {
            String body = mapper.writeValueAsString(Map.of("postId", postId, "coverUrl", coverUrl));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            catalogUrl + "/internal/items/" + encode(itemId) + "/cover"))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Internal-Key", internalKey)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 204) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo actualizar la portada del catálogo");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Actualización de portada interrumpida");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible");
        }
    }

    public void deleteCreatedItem(String postId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            catalogUrl + "/internal/items/by-post/" + encode(postId)))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Internal-Key", internalKey)
                    .DELETE()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 204 && response.statusCode() != 404) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo revertir la creación del Rankable Item");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reversión del Rankable Item interrumpida");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo no disponible para revertir el Rankable Item");
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
