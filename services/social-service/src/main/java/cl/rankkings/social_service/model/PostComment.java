package cl.rankkings.social_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="comments") @Getter @Setter
public class PostComment {
    @Id private String id;
    @Column(nullable=false) private String postId;
    @Column(nullable=false) private String authorId;
    private String parentId;
    private String replyToUsername;
    @Column(nullable=false,length=2000) private String text;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
