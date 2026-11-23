package cl.rankkings.api_gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {
    private static final int GENERAL_LIMIT = 300;
    private static final int AUTH_LIMIT = 20;
    private static final long WINDOW_SECONDS = 60;
    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return -80;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean authEndpoint = path.contains("/auth/");
        int limit = authEndpoint ? AUTH_LIMIT : GENERAL_LIMIT;
        String key = clientKey(exchange) + "|" + (authEndpoint ? "auth" : "general");
        long now = Instant.now().getEpochSecond();

        Deque<Long> timestamps = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_SECONDS) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
                return exchange.getResponse().setComplete();
            }
            timestamps.addLast(now);
        }
        return chain.filter(exchange);
    }

    private String clientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
