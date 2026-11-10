package cl.rankkings.social_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="post_ratings",uniqueConstraints=@UniqueConstraint(columnNames={"post_id","user_id"})) @Getter @Setter
public class PostRating {
    @Id private String id;
    @Column(name="post_id",nullable=false) private String postId;
    @Column(name="user_id",nullable=false) private String userId;
    @Column(nullable=false) private double score;
}
