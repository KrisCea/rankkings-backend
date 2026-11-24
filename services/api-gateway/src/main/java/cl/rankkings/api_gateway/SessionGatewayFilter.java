package cl.rankkings.api_gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.*;

/** Gateway: nunca confía en cabeceras de identidad suministradas por el navegador. */
@Component
public class SessionGatewayFilter implements GlobalFilter, Ordered {
    private final WebClient client;
    private final String identityUrl;
    private final String internalKey;
    private final String allowedOrigins;
    public record Principal(String id,String username,String displayName,String avatarUrl,String accountType,boolean isVerified,String role) {}
    @Autowired
    public SessionGatewayFilter(
            @Value("${rankkings.identity-url:http://localhost:8081}") String identityUrl,
            @Value("${rankkings.internal-key:}") String internalKey,
            @Value("${rankkings.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins
    ) {
        this(WebClient.create(), identityUrl, internalKey, allowedOrigins);
    }

    SessionGatewayFilter(WebClient client, String identityUrl, String internalKey, String allowedOrigins) {
        this.client = client;
        this.identityUrl = identityUrl;
        this.internalKey = internalKey;
        this.allowedOrigins = allowedOrigins;
    }
    @Override public int getOrder(){return -100;}

    @Override public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
        String token = tokenFrom(exchange.getRequest());
        ServerHttpRequest clean=exchange.getRequest().mutate().headers(h->{
            h.remove("X-User-Id");h.remove("X-Username");h.remove("X-User-Role");h.remove("X-Internal-Key");
            h.remove("Authorization");
            if(!internalKey.isBlank())h.set("X-Internal-Key",internalKey);
        }).build();
        ServerWebExchange next=exchange.mutate().request(clean).build();
        HttpMethod method=clean.getMethod();
        boolean unsafe=method!=HttpMethod.GET && method!=HttpMethod.HEAD && method!=HttpMethod.OPTIONS;
        String origin=clean.getHeaders().getOrigin();
        if(unsafe && origin!=null && !Arrays.stream(allowedOrigins.split(",")).map(String::trim).anyMatch(origin::equals)){
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        if (token == null || token.isBlank()) return chain.filter(next);

        if (internalKey.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return introspect(token)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(result -> {
                    if (result.isEmpty()) {
                        return chain.filter(next);
                    }

                    Principal principal = result.get();
                    if (isAdminRoute(clean.getURI().getPath()) && !"admin".equals(principal.role())) {
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }

                    ServerHttpRequest authenticated = clean.mutate().headers(h -> {
                        h.remove("Authorization");
                        h.set("X-User-Id", principal.id());
                        h.set("X-Username", principal.username());
                        if (principal.role() != null) h.set("X-User-Role", principal.role());
                    }).build();
                    return chain.filter(exchange.mutate().request(authenticated).build());
                });
    }

    private String tokenFrom(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        var cookie = request.getCookies().getFirst("rk_token");
        return cookie == null ? null : cookie.getValue();
    }

    private Mono<Principal> introspect(String token) {
        return client.get()
                .uri(identityUrl + "/internal/session")
                .header("X-Internal-Key", internalKey)
                .cookie("rk_token", token)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                        return Mono.empty();
                    }
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(Principal.class);
                })
                .onErrorMap(WebClientException.class, error -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo validar la sesión con Identity",
                        error
                ));
    }

    private boolean isAdminRoute(String path) {
        return path.contains("/admin/");
    }
}
