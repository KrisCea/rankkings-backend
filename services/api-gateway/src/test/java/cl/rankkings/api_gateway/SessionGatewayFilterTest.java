package cl.rankkings.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionGatewayFilterTest {
    private static final String TOKEN = "signed-session-token";
    private static final String INTERNAL_KEY = "gateway-internal-key";
    private static final String PRINCIPAL = """
            {
              "id": "account-42",
              "username": "sample-user",
              "displayName": "Sample User",
              "avatarUrl": null,
              "accountType": "user",
              "isVerifiedBusiness": false,
              "role": "user"
            }
            """;

    @Test
    void revokedJwtStopsAuthenticatingAfterIdentityDeletesItsSession() {
        AtomicBoolean sessionActive = new AtomicBoolean(true);
        AtomicInteger introspectionCalls = new AtomicInteger();
        ExchangeFunction identity = request -> {
            introspectionCalls.incrementAndGet();
            assertEquals("http://identity.test/internal/session", request.url().toString());
            assertEquals(INTERNAL_KEY, request.headers().getFirst("X-Internal-Key"));
            assertEquals(TOKEN, request.cookies().getFirst("rk_token"));
            if (!sessionActive.get()) {
                return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(PRINCIPAL)
                    .build());
        };
        SessionGatewayFilter filter = createFilter(identity);

        AtomicReference<ServerHttpRequest> firstDownstreamRequest = new AtomicReference<>();
        filter.filter(requestWithToken(), capture(firstDownstreamRequest)).block();
        assertEquals("account-42", firstDownstreamRequest.get().getHeaders().getFirst("X-User-Id"));
        assertNull(firstDownstreamRequest.get().getHeaders().getFirst("Authorization"));
        assertEquals(INTERNAL_KEY, firstDownstreamRequest.get().getHeaders().getFirst("X-Internal-Key"));

        sessionActive.set(false);
        AtomicReference<ServerHttpRequest> revokedDownstreamRequest = new AtomicReference<>();
        filter.filter(requestWithToken(), capture(revokedDownstreamRequest)).block();
        assertNull(revokedDownstreamRequest.get().getHeaders().getFirst("X-User-Id"));
        assertNull(revokedDownstreamRequest.get().getHeaders().getFirst("X-User-Role"));
        assertNull(revokedDownstreamRequest.get().getHeaders().getFirst("Authorization"));
        assertEquals(2, introspectionCalls.get());
    }

    @Test
    void identityFailureDoesNotSilentlyAuthenticateAsGuest() {
        ExchangeFunction identity = request -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        SessionGatewayFilter filter = createFilter(identity);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> filter.filter(requestWithToken(), ignored -> Mono.empty()).block()
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    private SessionGatewayFilter createFilter(ExchangeFunction identity) {
        WebClient client = WebClient.builder().exchangeFunction(identity).build();
        return new SessionGatewayFilter(client, "http://identity.test", INTERNAL_KEY, "https://app.example.test");
    }

    private MockServerWebExchange requestWithToken() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + TOKEN)
                .header("X-User-Id", "untrusted-client-id")
                .header("X-User-Role", "admin")
                .build());
    }

    private org.springframework.cloud.gateway.filter.GatewayFilterChain capture(
            AtomicReference<ServerHttpRequest> downstreamRequest
    ) {
        return exchange -> {
            downstreamRequest.set(exchange.getRequest());
            return Mono.empty();
        };
    }
}
