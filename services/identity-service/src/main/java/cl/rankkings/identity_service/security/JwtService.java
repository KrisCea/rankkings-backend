package cl.rankkings.identity_service.security;

import cl.rankkings.identity_service.model.Account;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(
            @Value("${rankkings.jwt-secret:}") String secret,
            @Value("${rankkings.jwt-ttl-seconds:604800}") long ttlSeconds
    ) {
        if (secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("RANKINGS_JWT_SECRET debe tener al menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public String createToken(Account account) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(account.getId())
                .id(UUID.randomUUID().toString())
                .claim("username", account.getUsername())
                .claim("role", normalizeRole(account.getRole()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    private String normalizeRole(String role) {
        return role == null || role.isBlank() ? "user" : role.toLowerCase();
    }
}
