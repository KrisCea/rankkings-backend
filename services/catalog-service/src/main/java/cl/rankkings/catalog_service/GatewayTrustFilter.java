package cl.rankkings.catalog_service;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Only allow HTTP traffic from the configured trusted API Gateway. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTrustFilter extends OncePerRequestFilter {
    @Value("${rankkings.internal-key:}") private String internalKey;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String given=request.getHeader("X-Internal-Key");
        if(internalKey.isBlank() || given==null || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8))){
            response.sendError(403,"Sólo API Gateway"); return;
        }
        chain.doFilter(request,response);
    }
}
