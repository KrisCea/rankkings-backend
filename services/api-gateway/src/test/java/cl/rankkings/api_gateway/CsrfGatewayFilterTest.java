package cl.rankkings.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsrfGatewayFilterTest {
    @Test
    void csrfEndpointCreatesReadableCookie() {
        CsrfGatewayFilter filter = new CsrfGatewayFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/csrf").build());

        filter.filter(exchange, ex -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(204, exchange.getResponse().getStatusCode().value());
        assertEquals(1, exchange.getResponse().getCookies().get("XSRF-TOKEN").size());
    }

    @Test
    void unsafeRequestRequiresMatchingToken() {
        CsrfGatewayFilter filter = new CsrfGatewayFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/posts")
                        .cookie(new org.springframework.http.HttpCookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "different")
                        .build());

        filter.filter(exchange, ex -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }
}
