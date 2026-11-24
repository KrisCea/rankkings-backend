package cl.rankkings.identity_service.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="accounts",uniqueConstraints={@UniqueConstraint(columnNames="username"),@UniqueConstraint(columnNames="email")})
@Getter @Setter
public class Account {
    @Id private String id;
    @Column(nullable=false,length=30) private String username;
    @Column(nullable=false,length=150) private String email;
    @Column(nullable=false,length=80) private String displayName;
    @Column(nullable=false,length=256) private String passwordHash;
    @Column(nullable=false) private boolean emailVerified;
    @Column(name="user_role",nullable=false) private String role="user";
    @Column(nullable=false) private String accountType="individual";
    @Column(length=512) private String avatarUrl;
    @Column(length=500) private String bio;
    @Column(name="verification_status",nullable=false,length=20) private String verificationStatus="unverified";
    @Column(name="verification_link",length=2048) private String verificationLink;
    @Column(name="verification_explanation",length=1000) private String verificationExplanation;
    @Column(name="verification_requested_at") private Instant verificationRequestedAt;
    @Column(name="verification_reviewed_at") private Instant verificationReviewedAt;
    @Column(name="verification_reviewed_by") private String verificationReviewedBy;
    @Column(name="verification_review_reason",length=1000) private String verificationReviewReason;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
