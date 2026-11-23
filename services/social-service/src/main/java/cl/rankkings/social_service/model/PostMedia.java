package cl.rankkings.social_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "post_media", indexes = {
        @Index(name = "idx_post_media_post_created", columnList = "post_id, created_at")
})
@Getter
@Setter
public class PostMedia {
    @Id
    private String id;

    @Column(name = "post_id", nullable = false)
    private String postId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(name = "is_cover")
    private Boolean cover = false;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "moderation_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'approved'")
    private String moderationStatus = "approved";

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderated_by")
    private String moderatedBy;

    @Column(name = "moderation_reason", length = 1000)
    private String moderationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
