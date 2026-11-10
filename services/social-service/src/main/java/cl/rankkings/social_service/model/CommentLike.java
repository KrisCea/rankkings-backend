package cl.rankkings.social_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="comment_likes",uniqueConstraints=@UniqueConstraint(columnNames={"comment_id","user_id"})) @Getter @Setter
public class CommentLike {
    @Id private String id;
    @Column(name="comment_id",nullable=false) private String commentId;
    @Column(name="user_id",nullable=false) private String userId;
}
