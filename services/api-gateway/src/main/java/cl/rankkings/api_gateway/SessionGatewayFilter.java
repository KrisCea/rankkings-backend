package cl.rankkings.api_gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.*;

/** Gateway: nunca confía en cabeceras de identidad suministradas por el navegador. */
@Component
public class SessionGatewayFilter implements GlobalFilter, Ordered {
    private final WebClient client=WebClient.create();
    @Value("${rankkings.identity-url:http://localhost:8081}") private String identityUrl;
    @Value("${rankkings.internal-key:}") private String internalKey;
    @Value("${rankkings.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") private String allowedOrigins;
    public record Principal(String id,String username,String displayName,String avatarUrl,String accountType,boolean isVerifiedBusiness) {}
    @Override public int getOrder(){return -100;}

    @Override public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
        ServerHttpRequest clean=exchange.getRequest().mutate().headers(h->{
            h.remove("X-User-Id");h.remove("X-Username");h.remove("X-Internal-Key");
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
        var cookie=clean.getCookies().getFirst("rk_token");
        if(cookie==null || cookie.getValue().isBlank() || internalKey.isBlank())return chain.filter(next);
        return client.get().uri(identityUrl+"/internal/session")
             .header("X-Internal-Key",internalKey).cookie("rk_token",cookie.getValue())
             .retrieve().bodyToMono(Principal.class)
             .map(principal -> {
                 ServerHttpRequest authenticated=clean.mutate().headers(h->{
                     h.set("X-User-Id",principal.id());h.set("X-Username",principal.username());
                 }).build();
                 return exchange.mutate().request(authenticated).build();
             })
             // Una sesión inválida se trata como visitante, sin reenviar identidad falsa.
             .onErrorResume(error->Mono.empty()).defaultIfEmpty(next).flatMap(chain::filter);
    }
}
