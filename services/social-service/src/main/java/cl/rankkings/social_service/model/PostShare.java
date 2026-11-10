package cl.rankkings.social_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="post_shares") @Getter @Setter
public class PostShare {
    @Id private String id;
    @Column(nullable=false) private String postId;
    @Column(nullable=false) private String senderId;
    @Column(nullable=false) private String recipientId;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
