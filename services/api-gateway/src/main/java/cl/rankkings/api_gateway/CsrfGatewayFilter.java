package cl.rankkings.api_gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CsrfGatewayFilter implements GlobalFilter, Ordered {
    private static final String COOKIE_NAME = "XSRF-TOKEN";
    private static final String HEADER_NAME = "X-XSRF-TOKEN";
    private final SecureRandom random = new SecureRandom();

    @Value("${rankkings.csrf-secure:false}")
    private boolean csrfSecure;

    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path.equals("/api/csrf") || path.equals("/api/v1/csrf")) {
            String token = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(randomBytes(32));
            exchange.getResponse().addCookie(ResponseCookie.from(COOKIE_NAME, token)
                    .httpOnly(false)
                    .secure(csrfSecure)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(java.time.Duration.ofHours(8))
                    .build());
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return exchange.getResponse().setComplete();
        }

        HttpMethod method = request.getMethod();
        if (method == null
                || method == HttpMethod.GET
                || method == HttpMethod.HEAD
                || method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String headerToken = request.getHeaders().getFirst(HEADER_NAME);
        var cookie = request.getCookies().getFirst(COOKIE_NAME);
        if (headerToken == null
                || cookie == null
                || headerToken.isBlank()
                || !MessageDigest.isEqual(
                        headerToken.getBytes(StandardCharsets.UTF_8),
                        cookie.getValue().getBytes(StandardCharsets.UTF_8))) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
