package cl.rankkings.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitGatewayFilterTest {
    @Test
    void authEndpointReturnsTooManyRequestsAfterLimit() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter();

        for (int i = 0; i < 21; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "198.51.100." + i + ", 203.0.113.10")
                            .build());
            filter.filter(exchange, ex -> Mono.empty()).block();
            if (i == 20) {
                assertEquals(429, exchange.getResponse().getStatusCode().value());
            }
        }
    }
}
