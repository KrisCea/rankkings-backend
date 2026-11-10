package cl.rankkings.catalog_service.config;

import cl.rankkings.catalog_service.model.RankableItem;
import cl.rankkings.catalog_service.model.RankableItem.CreatorEmbed;
import cl.rankkings.catalog_service.model.RankableItem.ExternalLinkEmbed;
import cl.rankkings.catalog_service.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DatosIniciales implements CommandLineRunner {

    private final ItemRepository itemRepository;
    @Value("${rankkings.seed-catalog:true}")
    private boolean seedCatalog;

    @Override
    public void run(String... args) throws IOException {
        seedOriginalExamples();
        if (seedCatalog) {
            seedCatalogCsv();
        }
    }

    private void seedOriginalExamples() {
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

    private void seedCatalogCsv() throws IOException {
        List<String> lines;
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/catalogo-ejemplo.csv").getInputStream(),
                StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("El archivo de semilla del catálogo está vacío");
        }

        String header = lines.getFirst().replaceFirst("^\uFEFF", "");
        if (!header.equals("itemId,categoria,titulo,creador,anio,padre,posicion,postId")) {
            throw new IllegalStateException("Encabezado inesperado en la semilla del catálogo");
        }

        List<SeedItem> seedItems = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> columns = parseCsvLine(lines.get(i), i + 1);
            if (columns.size() != 8) {
                throw new IllegalStateException("La fila " + (i + 1) + " de la semilla debe tener 8 columnas");
            }
            seedItems.add(new SeedItem(
                    columns.get(0),
                    columns.get(1),
                    columns.get(2),
                    columns.get(3),
                    parseOptionalInteger(columns.get(4), i + 1),
                    emptyToNull(columns.get(5)),
                    parseOptionalInteger(columns.get(6), i + 1)
            ));
        }

        Map<String, RankableItem> existingItems = itemRepository.findAllById(
                        seedItems.stream().map(SeedItem::id).toList())
                .stream()
                .collect(Collectors.toMap(RankableItem::getId, item -> item));

        List<RankableItem> missingItems = seedItems.stream()
                .filter(seed -> !existingItems.containsKey(seed.id()))
                .map(this::toRankableItem)
                .toList();
        itemRepository.saveAll(missingItems);

        Map<String, Long> childrenPerParent = seedItems.stream()
                .filter(seed -> seed.parentId() != null)
                .collect(Collectors.groupingBy(SeedItem::parentId, Collectors.counting()));
        childrenPerParent.forEach((parentId, count) -> itemRepository.findById(parentId).ifPresent(parent -> {
            parent.setChildrenCount(Math.max(parent.getChildrenCount(), Math.toIntExact(count)));
            itemRepository.save(parent);
        }));
    }

    private RankableItem toRankableItem(SeedItem seed) {
        String creatorId = "creator-seed-" + UUID.nameUUIDFromBytes(
                seed.creator().getBytes(StandardCharsets.UTF_8));
        return RankableItem.builder()
                .id(seed.id())
                .category(seed.category())
                .title(seed.title())
                .creator(new CreatorEmbed(creatorId, seed.creator(), null, null, null))
                .uploadedBy("catalog-seed")
                .year(seed.year())
                .coverUrl("")
                .identifiers(Map.of())
                .verificationStatus("unverified")
                // La semilla crea obras de catálogo, no publicaciones sociales.
                .postId("")
                .parentId(seed.parentId())
                .position(seed.position())
                .externalLinks(List.of())
                .build();
    }

    private List<String> parseCsvLine(String line, int lineNumber) {
        List<String> columns = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                columns.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Comillas sin cerrar en la fila " + lineNumber + " de la semilla");
        }
        columns.add(value.toString());
        return columns;
    }

    private Integer parseOptionalInteger(String value, int lineNumber) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Número inválido en la fila " + lineNumber + " de la semilla", exception);
        }
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private record SeedItem(
            String id,
            String category,
            String title,
            String creator,
            Integer year,
            String parentId,
            Integer position
    ) {}
}
