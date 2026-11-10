package cl.rankkings.catalog_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "saved_items", uniqueConstraints = {
    @UniqueConstraint(name = "uk_saved_user_item", columnNames = {"userId", "itemId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String itemId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant savedAt = Instant.now();
}
