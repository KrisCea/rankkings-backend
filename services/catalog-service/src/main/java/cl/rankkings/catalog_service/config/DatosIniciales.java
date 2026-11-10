package cl.rankkings.catalog_service.config;

import cl.rankkings.catalog_service.model.RankableItem;
import cl.rankkings.catalog_service.model.RankableItem.CreatorEmbed;
import cl.rankkings.catalog_service.model.RankableItem.ExternalLinkEmbed;
import cl.rankkings.catalog_service.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DatosIniciales implements CommandLineRunner {

    private final ItemRepository itemRepository;

    @Override
    public void run(String... args) {
        if (itemRepository.count() > 0) {
            return;
        }

        CreatorEmbed creatorMusic = new CreatorEmbed(
                "creator-1", "Daft Punk",
                "https://api.dicebear.com/7.x/identicon/svg?seed=daftpunk",
                null, null
        );

        RankableItem album = RankableItem.builder()
                .id("item-1")
                .category("music")
                .title("Discovery")
                .description("Segundo álbum de estudio del dúo francés Daft Punk.")
                .creator(creatorMusic)
                .uploadedBy("user-1")
                .year(2001)
                .coverUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600")
                .identifiers(Map.of("upc", "724384960650"))
                .verificationStatus("verified")
                .postId("post-1")
                .averageRating(9.4)
                .ratingsCount(12)
                .childrenCount(2)
                .childrenAverageRating(9.3)
                .savesCount(0)
                .externalLinks(List.of(
                        new ExternalLinkEmbed("link-1", "Spotify", "https://open.spotify.com", "streaming")
                ))
                .build();

        RankableItem track1 = RankableItem.builder()
                .id("item-2")
                .category("music")
                .title("One More Time")
                .creator(creatorMusic)
                .uploadedBy("user-1")
                .year(2000)
                .coverUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600")
                .identifiers(Map.of("isrc", "GBDUW0000053"))
                .verificationStatus("verified")
                .postId("post-2")
                .parentId("item-1")
                .position(1)
                .averageRating(9.5)
                .ratingsCount(8)
                .build();

        RankableItem track2 = RankableItem.builder()
                .id("item-3")
                .category("music")
                .title("Digital Love")
                .creator(creatorMusic)
                .uploadedBy("user-1")
                .year(2001)
                .coverUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600")
                .identifiers(Map.of("isrc", "GBDUW0100003"))
                .verificationStatus("verified")
                .postId("post-3")
                .parentId("item-1")
                .position(2)
                .averageRating(9.1)
                .ratingsCount(5)
                .build();

        itemRepository.saveAll(List.of(album, track1, track2));
    }
}
