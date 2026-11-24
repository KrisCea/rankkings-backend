package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.PostMedia;
import cl.rankkings.social_service.model.PostRating;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {
    private static final List<String> TYPES = List.of("item", "post", "review", "comment", "media");
    private static final List<String> STATUSES = List.of("pending", "approved", "rejected");

    private final PostRepository posts;
    private final CommentRepository comments;
    private final RatingRepository ratings;
    private final PostMediaService postMediaService;
    private final CatalogServiceClient catalogClient;
    private final IdentityServiceClient identityClient;

    @Transactional(readOnly = true)
    public Map<String, Object> queue(String status, String type, int limit, String cursor, String viewerId) {
        requireAdmin(viewerId);
        if (!STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de moderación inválido");
        }
        if (!"all".equals(type) && !TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de moderación inválido");
        }
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit debe estar entre 1 y 100");
        }

        List<Entry> entries = new ArrayList<>();
        if ("all".equals(type) || "post".equals(type)) {
            posts.findByModerationStatusOrderByCreatedAtDesc(status).stream()
                    .filter(post -> "item".equals(post.getKind()))
                    .forEach(post -> entries.add(postEntry(post)));
        }
        if ("all".equals(type) || "item".equals(type)) {
            catalogClient.getModerationItems(status).forEach(item -> entries.add(itemEntry(item)));
        }
        if ("all".equals(type) || "review".equals(type)) {
            comments.findByKindAndModerationStatusAndDeletedAtIsNullOrderByCreatedAtDesc("review", status)
                    .forEach(comment -> entries.add(reviewEntry(comment)));
        }
        if ("all".equals(type) || "comment".equals(type)) {
            comments.findByKindAndModerationStatusAndDeletedAtIsNullOrderByCreatedAtDesc("comment", status)
                    .forEach(comment -> entries.add(commentEntry(comment)));
        }
        if ("all".equals(type) || "media".equals(type)) {
            postMediaService.moderationMedia(status).forEach(media -> entries.add(mediaEntry(media)));
        }

        entries.sort(Comparator.comparing(Entry::submittedAt).reversed()
                .thenComparing(Entry::type)
                .thenComparing(Entry::id));
        if (cursor != null && !cursor.isBlank()) {
            Entry cursorEntry = decodeCursor(cursor);
            entries.removeIf(entry -> ENTRY_ORDER.compare(entry, cursorEntry) <= 0);
        }

        boolean hasMore = entries.size() > limit;
        List<Entry> pageItems = entries.stream().limit(limit).toList();
        List<Map<String, Object>> items = pageItems.stream().map(Entry::item).toList();
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? encodeCursor(pageItems.get(pageItems.size() - 1))
                : null;

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("limit", limit);
        page.put("nextCursor", nextCursor);
        page.put("hasMore", hasMore);
        return Map.of("items", items, "page", page);
    }

    @Transactional
    public Map<String, Object> decide(
            String type,
            String id,
            boolean approve,
            String reason,
            String moderatorId
    ) {
        requireAdmin(moderatorId);
        if (!TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de moderación inválido");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica un motivo para rechazar");
        }
        String status = approve ? "approved" : "rejected";
        String decisionReason = approve ? null : reason.trim();
        Instant moderatedAt = Instant.now();

        switch (type) {
            case "item" -> moderateItem(id, moderatorId, status, decisionReason, moderatedAt);
            case "post" -> moderatePost(id, moderatorId, status, decisionReason, moderatedAt);
            case "review" -> moderateComment(id, "review", moderatorId, status, decisionReason, moderatedAt);
            case "comment" -> moderateComment(id, "comment", moderatorId, status, decisionReason, moderatedAt);
            case "media" -> postMediaService.moderateMedia(id, moderatorId, status, decisionReason, moderatedAt);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de moderación inválido");
        }
        return decision(id, type, status, moderatedAt, moderatorId, decisionReason);
    }

    private void moderatePost(String id, String moderatorId, String status, String reason, Instant moderatedAt) {
        SocialPost post = posts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
        requirePending(post.getModerationStatus());
        post.setModerationStatus(status);
        post.setModeratedAt(moderatedAt);
        post.setModeratedBy(moderatorId);
        post.setModerationReason(reason);
        posts.save(post);

        if ("item".equals(post.getKind())) {
            JsonNode item = catalogClient.getItem(post.getItemId(), null);
            if (post.getId().equals(item.path("postId").asString(null))
                    && "pending".equals(item.path("moderationStatus").asString(""))) {
                catalogClient.updateItemModerationStatus(
                        post.getItemId(), post.getId(), status, moderatorId, reason, moderatedAt);
            }
        }
        if ("approved".equals(status)) {
            postMediaService.syncApprovedCoverForPost(post);
        }
    }

    private void moderateItem(String id, String moderatorId, String status, String reason, Instant moderatedAt) {
        JsonNode item = catalogClient.getItem(id, null);
        requirePending(item.path("moderationStatus").asString(""));
        String postId = item.path("postId").asString("");
        if (postId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El Rankable Item no tiene una publicación asociada");
        }
        catalogClient.updateItemModerationStatus(id, postId, status, moderatorId, reason, moderatedAt);
    }

    private void moderateComment(
            String id,
            String kind,
            String moderatorId,
            String status,
            String reason,
            Instant moderatedAt
    ) {
        PostComment comment = comments.findByIdAndDeletedAtIsNullForUpdate(id)
                .filter(value -> kind.equals(value.getKind()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no encontrado"));
        requirePending(comment.getModerationStatus());
        comment.setModerationStatus(status);
        comment.setModeratedAt(moderatedAt);
        comment.setModeratedBy(moderatorId);
        comment.setModerationReason(reason);
        comments.save(comment);

        if ("review".equals(kind) && "approved".equals(status) && comment.getAuthorScore() != null) {
            PostRating rating = ratings.findByPostIdAndUserId(comment.getPostId(), comment.getAuthorId())
                    .orElseGet(() -> {
                        PostRating created = new PostRating();
                        created.setId(UUID.randomUUID().toString());
                        created.setPostId(comment.getPostId());
                        created.setUserId(comment.getAuthorId());
                        return created;
                    });
            rating.setScore(comment.getAuthorScore());
            ratings.saveAndFlush(rating);
            syncCatalogRatings(comment.getPostId());
        }
    }

    private void syncCatalogRatings(String postId) {
        SocialPost post = posts.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
        if (!"item".equals(post.getKind())) {
            return;
        }
        List<PostRating> postRatings = ratings.findByPostId(postId);
        double average = postRatings.stream().mapToDouble(PostRating::getScore).average().orElse(0.0);
        catalogClient.updateRatingStats(post.getItemId(), average, postRatings.size());
    }

    private Entry postEntry(SocialPost post) {
        JsonNode item = catalogClient.getItem(post.getItemId(), null);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("kind", post.getKind());
        content.put("caption", post.getCaption());
        Map<String, Object> itemInfo = new LinkedHashMap<>();
        itemInfo.put("id", item.path("id").asString(""));
        itemInfo.put("title", item.path("title").asString(""));
        itemInfo.put("category", item.path("category").asString(""));
        itemInfo.put("coverUrl", item.path("coverUrl").asString(null));
        content.put("item", itemInfo);
        return entry(post.getId(), "post", post.getCreatedAt(), post.getModerationStatus(), post.getAuthorId(), content);
    }

    private Entry itemEntry(JsonNode item) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", item.path("title").asString(""));
        content.put("category", item.path("category").asString(""));
        content.put("description", item.path("description").asString(""));
        content.put("coverUrl", item.path("coverUrl").asString(null));
        content.put("postId", item.path("postId").asString(""));
        return entry(
                item.path("id").asString(""),
                "item",
                Instant.parse(item.path("createdAt").asString()),
                item.path("moderationStatus").asString(""),
                item.path("uploadedBy").asString(""),
                content
        );
    }

    private Entry reviewEntry(PostComment comment) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("postId", comment.getPostId());
        content.put("itemId", postItemId(comment.getPostId()));
        content.put("text", comment.getText());
        content.put("authorScore", comment.getAuthorScore());
        return entry(comment.getId(), "review", comment.getCreatedAt(), comment.getModerationStatus(),
                comment.getAuthorId(), content);
    }

    private Entry commentEntry(PostComment comment) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("postId", comment.getPostId());
        content.put("parentId", comment.getParentId());
        content.put("text", comment.getText());
        return entry(comment.getId(), "comment", comment.getCreatedAt(), comment.getModerationStatus(),
                comment.getAuthorId(), content);
    }

    private Entry mediaEntry(PostMedia media) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("postId", media.getPostId());
        content.put("mediaType", media.getMediaType());
        content.put("url", postMediaService.moderationUrl(media));
        content.put("contentType", media.getContentType());
        content.put("isCover", Boolean.TRUE.equals(media.getCover()));
        return entry(media.getId(), "media", media.getCreatedAt(), media.getModerationStatus(),
                media.getOwnerId(), content);
    }

    private String postItemId(String postId) {
        return posts.findById(postId)
                .map(SocialPost::getItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
    }

    private Entry entry(
            String id,
            String type,
            Instant submittedAt,
            String status,
            String authorId,
            Map<String, Object> content
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("type", type);
        item.put("status", status);
        item.put("submittedAt", submittedAt.toString());
        JsonNode author = identityClient.getUser(authorId);
        item.put("author", Map.of(
                "id", authorId,
                "username", author.path("username").asString("")
        ));
        item.put("content", content);
        return new Entry(id, type, submittedAt, item);
    }

    private Map<String, Object> decision(
            String id,
            String type,
            String status,
            Instant moderatedAt,
            String moderatorId,
            String reason
    ) {
        JsonNode moderator = identityClient.getUser(moderatorId);
        Map<String, Object> moderatedBy = Map.of(
                "id", moderatorId,
                "username", moderator.path("username").asString("")
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("type", type);
        response.put("status", status);
        response.put("moderatedAt", moderatedAt.toString());
        response.put("moderatedBy", moderatedBy);
        response.put("reason", reason);
        return response;
    }

    private void requirePending(String status) {
        if (!"pending".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El contenido ya fue moderado");
        }
    }

    private void requireAdmin(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión");
        }
        if (!"admin".equals(identityClient.getUser(userId).path("role").asString(""))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol admin");
        }
    }

    private String encodeCursor(Entry entry) {
        String raw = entry.submittedAt() + "|" + entry.type() + "|" + entry.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Entry decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            if (parts.length != 3 || !TYPES.contains(parts[1])) {
                throw new IllegalArgumentException();
            }
            return new Entry(parts[2], parts[1], Instant.parse(parts[0]), Map.of());
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido");
        }
    }

    private static final Comparator<Entry> ENTRY_ORDER = Comparator.comparing(Entry::submittedAt).reversed()
            .thenComparing(Entry::type)
            .thenComparing(Entry::id);

    private record Entry(String id, String type, Instant submittedAt, Map<String, Object> item) {}
}
