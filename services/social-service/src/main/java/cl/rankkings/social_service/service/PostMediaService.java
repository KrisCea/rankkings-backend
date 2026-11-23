package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.dto.SocialDtos.ExternalMediaInput;
import cl.rankkings.social_service.dto.SocialDtos.ImageUploadInput;
import cl.rankkings.social_service.model.PostMedia;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.PostMediaRepository;
import cl.rankkings.social_service.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostMediaService {
    private static final String PENDING_UPLOAD = "pending_upload";
    private static final String READY = "ready";
    private static final Duration PENDING_UPLOAD_TTL = Duration.ofMinutes(10);

    private final PostRepository posts;
    private final PostMediaRepository media;
    private final S3MediaStorage storage;
    private final CatalogServiceClient catalogClient;
    private final IdentityServiceClient identityClient;

    @Transactional
    public Map<String, Object> prepareImageUpload(String postId, ImageUploadInput input, String viewerId) {
        SocialPost post = requireOwnedItemPost(postId, viewerId);
        PostMediaPolicy.validateImage(input.contentType(), input.sizeBytes());
        checkCapacity(postId, input.sizeBytes());

        String mediaId = UUID.randomUUID().toString();
        String objectKey = "pending-post-media/" + mediaId;
        S3MediaStorage.SignedUrl signedUrl = storage.presignUpload(
                objectKey,
                input.contentType(),
                input.sizeBytes()
        );

        PostMedia attachment = new PostMedia();
        attachment.setId(mediaId);
        attachment.setPostId(post.getId());
        attachment.setOwnerId(viewerId);
        attachment.setMediaType("image");
        attachment.setCover(input.isCover());
        attachment.setContentType(input.contentType());
        attachment.setSizeBytes(input.sizeBytes());
        attachment.setObjectKey(objectKey);
        attachment.setStatus(PENDING_UPLOAD);
        attachment.setModerationStatus("pending");
        media.save(attachment);

        return Map.of(
                "mediaId", mediaId,
                "uploadUrl", signedUrl.url(),
                "method", "PUT",
                "headers", Map.of(
                        "Content-Type", input.contentType(),
                        "If-None-Match", "*"
                ),
                "expiresAt", signedUrl.expiresAt()
        );
    }

    @Transactional
    public Map<String, String> confirmImageUpload(String postId, String mediaId, String viewerId) {
        SocialPost post = requireOwnedItemPost(postId, viewerId);
        PostMedia attachment = requireOwnedMedia(postId, mediaId, viewerId);
        if (!PENDING_UPLOAD.equals(attachment.getStatus())) {
            if (READY.equals(attachment.getStatus())) {
                return Map.of("mediaId", mediaId, "status", READY);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La carga ya fue confirmada");
        }

        S3MediaStorage.ObjectMetadata metadata;
        try {
            metadata = storage.head(attachment.getObjectKey());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero debes subir el archivo a S3");
            }
            throw ex;
        }

        if (metadata.contentLength() != attachment.getSizeBytes()
                || !attachment.getContentType().equals(metadata.contentType())
                || !storage.hasValidImageSignature(attachment.getObjectKey(), attachment.getContentType())) {
            storage.delete(attachment.getObjectKey());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo subido no coincide con la imagen declarada");
        }

        attachment.setObjectKey(storage.finalizeUpload(
                attachment.getObjectKey(),
                mediaId,
                attachment.getContentType()
        ));
        attachment.setStatus(READY);
        attachment.setModerationStatus("pending");
        media.save(attachment);
        return Map.of("mediaId", mediaId, "status", READY);
    }

    @Transactional
    public Map<String, String> addExternalMedia(String postId, ExternalMediaInput input, String viewerId) {
        SocialPost post = requireOwnedItemPost(postId, viewerId);
        if (!"video".equals(input.type()) && !"external".equals(input.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace debe ser de tipo video o external");
        }
        checkCapacity(postId, 0);

        PostMedia attachment = new PostMedia();
        attachment.setId(UUID.randomUUID().toString());
        attachment.setPostId(post.getId());
        attachment.setOwnerId(viewerId);
        attachment.setMediaType(input.type());
        attachment.setExternalUrl(PostMediaPolicy.normalizeExternalUrl(input.url()));
        attachment.setStatus(READY);
        attachment.setModerationStatus("pending");
        media.save(attachment);
        return Map.of("mediaId", attachment.getId(), "status", READY);
    }

    @Transactional
    public void deleteMedia(String postId, String mediaId, String viewerId) {
        SocialPost post = requirePostOrAdmin(postId, viewerId);
        PostMedia attachment = requireOwnedMediaOrAdmin(postId, mediaId, viewerId);
        boolean deletingActiveCover = READY.equals(attachment.getStatus())
                && "approved".equals(attachment.getModerationStatus())
                && Boolean.TRUE.equals(attachment.getCover());
        if (attachment.getObjectKey() != null) {
            storage.delete(attachment.getObjectKey());
        }
        media.delete(attachment);
        if (deletingActiveCover && "approved".equals(post.getModerationStatus())) {
            syncItemCover(post, "");
        }
    }

    @Transactional
    public void deleteForPost(String postId) {
        List<PostMedia> attachments = media.findByPostIdOrderByCreatedAtAsc(postId);
        media.deleteAll(attachments);
        List<String> objectKeys = attachments.stream()
                .map(PostMedia::getObjectKey)
                .filter(key -> key != null && !key.isBlank())
                .toList();
        if (objectKeys.isEmpty()) {
            return;
        }

        Runnable cleanup = () -> objectKeys.forEach(objectKey -> {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException exception) {
                log.error("No se pudo borrar el objeto multimedia {} del post {}", objectKey, postId, exception);
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup.run();
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mediaForPost(String postId) {
        return mediaForPost(postId, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mediaForPost(String postId, String viewerId) {
        List<PostMedia> visible = new java.util.ArrayList<>(
                media.findByPostIdAndStatusAndModerationStatusOrderByCreatedAtAsc(postId, READY, "approved"));
        if (viewerId != null && !viewerId.isBlank()) {
            List<PostMedia> pending = media.findByPostIdAndStatusOrderByCreatedAtAsc(postId, READY).stream()
                    .filter(attachment -> !"approved".equals(attachment.getModerationStatus()))
                    .toList();
            visible.addAll(pending.stream()
                    .filter(attachment -> viewerId.equals(attachment.getOwnerId()))
                    .toList());
            if (pending.stream().anyMatch(attachment -> !viewerId.equals(attachment.getOwnerId()))
                    && isAdmin(viewerId)) {
                pending.stream()
                        .filter(attachment -> !viewerId.equals(attachment.getOwnerId()))
                        .forEach(visible::add);
            }
        }
        return visible
                .stream()
                .map(attachment -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", attachment.getId());
                    result.put("url", moderationUrl(attachment));
                    result.put("type", attachment.getMediaType());
                    result.put("isCover", Boolean.TRUE.equals(attachment.getCover()));
                    return result;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostMedia> moderationMedia(String moderationStatus) {
        return media.findByStatusAndModerationStatusOrderByCreatedAtDesc(READY, moderationStatus);
    }

    @Transactional(readOnly = true)
    public String moderationUrl(PostMedia attachment) {
        return attachment.getObjectKey() == null
                ? attachment.getExternalUrl()
                : storage.presignRead(attachment.getObjectKey());
    }

    @Transactional
    public PostMedia moderateMedia(
            String mediaId,
            String moderatorId,
            String moderationStatus,
            String reason,
            Instant moderatedAt
    ) {
        PostMedia attachment = media.findByIdForUpdate(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        if (!READY.equals(attachment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La carga del adjunto aún no se completó");
        }
        if (!"pending".equals(attachment.getModerationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El adjunto ya fue moderado");
        }

        attachment.setModerationStatus(moderationStatus);
        attachment.setModeratedAt(moderatedAt);
        attachment.setModeratedBy(moderatorId);
        attachment.setModerationReason(reason);
        media.save(attachment);

        if ("approved".equals(moderationStatus) && Boolean.TRUE.equals(attachment.getCover())) {
            SocialPost post = posts.findById(attachment.getPostId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
            media.findByPostIdAndCoverTrue(post.getId()).stream()
                    .filter(existing -> !existing.getId().equals(mediaId)
                            && "approved".equals(existing.getModerationStatus()))
                    .forEach(existing -> {
                        existing.setCover(false);
                        media.save(existing);
                    });
            if ("approved".equals(post.getModerationStatus())) {
                syncItemCover(post, attachment);
            }
        }
        return attachment;
    }

    @Transactional
    public void syncApprovedCoverForPost(SocialPost post) {
        if (!"approved".equals(post.getModerationStatus())) {
            return;
        }
        media.findByPostIdAndCoverTrue(post.getId()).stream()
                .filter(attachment -> READY.equals(attachment.getStatus())
                        && "approved".equals(attachment.getModerationStatus()))
                .findFirst()
                .ifPresent(attachment -> syncItemCover(post, attachment));
    }

    @Transactional(readOnly = true)
    public String mediaContentUrl(String postId, String mediaId, String viewerId) {
        SocialPost post = posts.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
        if (!"approved".equals(post.getModerationStatus())
                && !post.getAuthorId().equals(viewerId)
                && (viewerId == null || !isAdmin(viewerId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        PostMedia attachment = media.findByIdAndPostId(mediaId, postId)
                .filter(item -> READY.equals(item.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
        if (!"approved".equals(attachment.getModerationStatus())
                && !attachment.getOwnerId().equals(viewerId)
                && (viewerId == null || !isAdmin(viewerId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        return moderationUrl(attachment);
    }

    @Transactional(readOnly = true)
    public String mediaContentUrl(String postId, String mediaId) {
        return mediaContentUrl(postId, mediaId, null);
    }

    private void checkCapacity(String postId, long additionalBytes) {
        List<PostMedia> existing = media.findByPostIdOrderByCreatedAtAsc(postId);
        Instant pendingCutoff = Instant.now().minus(PENDING_UPLOAD_TTL);
        for (PostMedia attachment : existing) {
            if (PENDING_UPLOAD.equals(attachment.getStatus())
                    && attachment.getCreatedAt().isBefore(pendingCutoff)) {
                storage.delete(attachment.getObjectKey());
                media.delete(attachment);
            }
        }
        existing = existing.stream()
                .filter(attachment -> !(PENDING_UPLOAD.equals(attachment.getStatus())
                        && attachment.getCreatedAt().isBefore(pendingCutoff)))
                .toList();
        long activeCount = existing.stream()
                .filter(attachment -> READY.equals(attachment.getStatus())
                        || PENDING_UPLOAD.equals(attachment.getStatus()))
                .count();
        long currentBytes = existing.stream()
                .filter(attachment -> READY.equals(attachment.getStatus())
                        || PENDING_UPLOAD.equals(attachment.getStatus()))
                .map(PostMedia::getSizeBytes)
                .filter(size -> size != null)
                .mapToLong(Long::longValue)
                .sum();

        if (activeCount >= PostMediaPolicy.MAX_ATTACHMENTS_PER_POST
                || currentBytes + additionalBytes > PostMediaPolicy.MAX_TOTAL_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El post admite hasta 4 adjuntos y 20 MB en total");
        }
    }

    private SocialPost requireOwnedItemPost(String postId, String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión");
        }
        SocialPost post = posts.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
        if (!"item".equals(post.getKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los adjuntos se agregan al post del Rankable Item");
        }
        if (!viewerId.equals(post.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede modificar los adjuntos");
        }
        return post;
    }

    private SocialPost requirePostOrAdmin(String postId, String viewerId) {
        requireUser(viewerId);
        SocialPost post = posts.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
        if (!"item".equals(post.getKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los adjuntos se agregan al post del Rankable Item");
        }
        if (!viewerId.equals(post.getAuthorId()) && !isAdmin(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor o un administrador puede quitar adjuntos");
        }
        return post;
    }

    private PostMedia requireOwnedMedia(String postId, String mediaId, String viewerId) {
        PostMedia attachment = media.findByIdAndPostId(mediaId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        if (!viewerId.equals(attachment.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede modificar los adjuntos");
        }
        return attachment;
    }

    private PostMedia requireOwnedMediaOrAdmin(String postId, String mediaId, String viewerId) {
        PostMedia attachment = media.findByIdAndPostId(mediaId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        if (!viewerId.equals(attachment.getOwnerId()) && !isAdmin(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor o un administrador puede quitar adjuntos");
        }
        return attachment;
    }

    private boolean isAdmin(String userId) {
        return "admin".equals(identityClient.getUser(userId).path("role").asString(""));
    }

    private void requireUser(String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión");
        }
    }

    private void syncItemCover(SocialPost post, PostMedia attachment) {
        String coverUrl = "/api/posts/%s/media/%s/content".formatted(
                UriUtils.encodePathSegment(post.getId(), StandardCharsets.UTF_8),
                UriUtils.encodePathSegment(attachment.getId(), StandardCharsets.UTF_8)
        );
        syncItemCover(post, coverUrl);
    }

    private void syncItemCover(SocialPost post, String coverUrl) {
        Runnable update = () -> {
            JsonNode item = catalogClient.getItem(post.getItemId(), null);
            if (post.getId().equals(item.path("postId").asString(null))) {
                catalogClient.updateItemCover(post.getItemId(), post.getId(), coverUrl);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            update.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                update.run();
            }
        });
    }
}
