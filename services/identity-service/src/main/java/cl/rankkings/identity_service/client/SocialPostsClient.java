package cl.rankkings.identity_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class SocialPostsClient {
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @Value("${rankkings.internal-key:}") private String internalKey;
    @Value("${rankkings.social-url:http://localhost:8083}") private String socialUrl;

    public SocialPostsClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public long countByAuthor(String userId) {
        if (internalKey.isBlank()) {
            return 0;
        }
        String encodedId = URLEncoder.encode(userId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(socialUrl + "/internal/users/" + encodedId + "/posts/count"))
                .timeout(Duration.ofSeconds(2))
                .header("X-Internal-Key", internalKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readTree(response.body()).path("count").asLong(0);
            }
            log.warn("Social Service returned status {} for post count of user {}", response.statusCode(), userId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while requesting post count for user {}", userId, exception);
        } catch (Exception exception) {
            log.warn("Could not retrieve post count for user {}", userId, exception);
        }
        return 0;
    }
}
