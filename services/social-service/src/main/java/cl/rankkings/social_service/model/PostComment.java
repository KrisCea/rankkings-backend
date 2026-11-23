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
    @Column(nullable=false, length=20, columnDefinition="varchar(20) default 'comment'") private String kind="comment";
    private String parentId;
    private String replyToUsername;
    @Column(nullable=false,length=2000) private String text;
    @Column(name="author_score") private Double authorScore;
    @Column(name="deleted_at") private Instant deletedAt;
    @Column(name="moderation_status", nullable=false, length=20, columnDefinition="varchar(20) default 'approved'") private String moderationStatus="approved";
    @Column(name="moderated_at") private Instant moderatedAt;
    @Column(name="moderated_by") private String moderatedBy;
    @Column(name="moderation_reason", length=1000) private String moderationReason;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
