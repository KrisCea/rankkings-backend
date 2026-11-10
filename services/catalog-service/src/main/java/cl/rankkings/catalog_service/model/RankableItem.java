package cl.rankkings.catalog_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "rankable_items", indexes = {
    @Index(name = "idx_items_category", columnList = "category"),
    @Index(name = "idx_items_parent", columnList = "parentId, position")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankableItem {

    @Id
    private String id;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private CreatorEmbed creator;

    @Column(nullable = false)
    private String uploadedBy;

    @Column(name = "release_year")
    private Integer year;

    @Column(nullable = false)
    private String coverUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private Map<String, String> identifiers = new HashMap<>();

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String verificationStatus = "unverified";

    @Column(nullable = false)
    private String postId;

    @Builder.Default
    private double averageRating = 0.0;

    @Builder.Default
    private int ratingsCount = 0;

    private String parentId;
    private Integer position;

    @Builder.Default
    private int childrenCount = 0;

    private Double childrenAverageRating;

    @Builder.Default
    private int savesCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<ExternalLinkEmbed> externalLinks = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public record CreatorEmbed(
            String id,
            String name,
            String avatarUrl,
            String linkedUserId,
            String linkedUsername
    ) {}

    public record ExternalLinkEmbed(
            String id,
            String label,
            String url,
            String type
    ) {}
}
