package cl.rankkings.identity_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="user_follows",uniqueConstraints=@UniqueConstraint(columnNames={"follower_id","followed_id"})) @Getter @Setter
public class FollowRelation {
    @Id private String id;
    @Column(name="follower_id",nullable=false) private String followerId;
    @Column(name="followed_id",nullable=false) private String followedId;
}
