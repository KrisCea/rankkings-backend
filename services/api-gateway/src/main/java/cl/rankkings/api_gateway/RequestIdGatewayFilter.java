package cl.rankkings.api_gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdGatewayFilter implements GlobalFilter, Ordered {
    public static final String HEADER = "X-Request-Id";

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String finalRequestId = requestId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, finalRequestId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER, finalRequestId);
        return chain.filter(exchange.mutate().request(request).build());
    }
}
