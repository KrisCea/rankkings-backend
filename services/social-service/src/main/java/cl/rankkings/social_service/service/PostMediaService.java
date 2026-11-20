package cl.rankkings.social_service.service;

import cl.rankkings.social_service.dto.SocialDtos.ExternalMediaInput;
import cl.rankkings.social_service.dto.SocialDtos.ImageUploadInput;
import cl.rankkings.social_service.model.PostMedia;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.PostMediaRepository;
import cl.rankkings.social_service.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostMediaService {
    private static final String PENDING_UPLOAD = "pending_upload";
    private static final String READY = "ready";
    private static final Duration PENDING_UPLOAD_TTL = Duration.ofMinutes(10);

    private final PostRepository posts;
    private final PostMediaRepository media;
    private final S3MediaStorage storage;

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
        attachment.setContentType(input.contentType());
        attachment.setSizeBytes(input.sizeBytes());
        attachment.setObjectKey(objectKey);
        attachment.setStatus(PENDING_UPLOAD);
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
        requireOwnedItemPost(postId, viewerId);
        PostMedia attachment = requireOwnedMedia(postId, mediaId, viewerId);
        if (!PENDING_UPLOAD.equals(attachment.getStatus())) {
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
        media.save(attachment);
        return Map.of("mediaId", attachment.getId(), "status", READY);
    }

    @Transactional
    public void deleteMedia(String postId, String mediaId, String viewerId) {
        requireOwnedItemPost(postId, viewerId);
        PostMedia attachment = requireOwnedMedia(postId, mediaId, viewerId);
        if (attachment.getObjectKey() != null) {
            storage.delete(attachment.getObjectKey());
        }
        media.delete(attachment);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mediaForPost(String postId) {
        return media.findByPostIdAndStatusOrderByCreatedAtAsc(postId, READY)
                .stream()
                .map(attachment -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", attachment.getId());
                    result.put("url", attachment.getObjectKey() == null
                            ? attachment.getExternalUrl()
                            : storage.presignRead(attachment.getObjectKey()));
                    result.put("type", attachment.getMediaType());
                    return result;
                })
                .toList();
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

    private PostMedia requireOwnedMedia(String postId, String mediaId, String viewerId) {
        PostMedia attachment = media.findByIdAndPostId(mediaId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        if (!viewerId.equals(attachment.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede modificar los adjuntos");
        }
        return attachment;
    }
}
