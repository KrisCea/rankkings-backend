package cl.rankkings.social_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="posts") @Getter @Setter
public class SocialPost {
    @Id private String id;
    @Column(nullable=false) private String authorId;
    @Column(nullable=false) private String itemId;
    @Column(nullable=false) private String kind;
    @Column(nullable=false,length=2000) private String caption="";
    private Double reviewScore;
    @Column(nullable=false) private String moderationStatus="approved";
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
