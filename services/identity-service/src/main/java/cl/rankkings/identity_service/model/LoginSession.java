package cl.rankkings.identity_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="login_sessions") @Getter @Setter
public class LoginSession {
    @Id @Column(length=64) private String tokenHash;
    @Column(nullable=false) private String accountId;
    @Column(nullable=false) private Instant expiresAt;
}
