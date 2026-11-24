package cl.rankkings.social_service.service;

import cl.rankkings.social_service.client.CatalogServiceClient;
import cl.rankkings.social_service.client.IdentityServiceClient;
import cl.rankkings.social_service.dto.SocialDtos.ImageUploadInput;
import cl.rankkings.social_service.model.PostMedia;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.PostMediaRepository;
import cl.rankkings.social_service.repository.PostRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMediaServiceTest {
    @Mock private PostRepository posts;
    @Mock private PostMediaRepository media;
    @Mock private S3MediaStorage storage;
    @Mock private CatalogServiceClient catalogClient;
    @Mock private IdentityServiceClient identityClient;
    @InjectMocks private PostMediaService postMediaService;

    @Test
    void treatsNullCoverFlagAsFalse() throws Exception {
        ImageUploadInput input = new ObjectMapper().readValue(
                "{\"contentType\":\"image/jpeg\",\"sizeBytes\":112847,\"isCover\":null}",
                ImageUploadInput.class
        );

        assertFalse(input.isCover());
    }

    @Test
    void preservesExistingCoverUntilNewImageIsUploaded() {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setAuthorId("user-1");
        post.setKind("item");
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(post));
        when(media.findByPostIdOrderByCreatedAtAsc("post-1")).thenReturn(List.of());
        when(storage.presignUpload(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("image/png"), org.mockito.ArgumentMatchers.eq(100L)))
                .thenReturn(new S3MediaStorage.SignedUrl("https://upload.example/image", "expires"));

        postMediaService.prepareImageUpload("post-1", new ImageUploadInput("image/png", 100L, true), "user-1");

        verify(media, never()).findByPostIdAndCoverTrue("post-1");
        ArgumentCaptor<PostMedia> uploaded = ArgumentCaptor.forClass(PostMedia.class);
        verify(media).save(uploaded.capture());
        assertTrue(uploaded.getValue().getCover());
        assertTrue(uploaded.getValue().getObjectKey().startsWith("pending-post-media/"));
    }

    @Test
    void makesNewCoverAvailableImmediatelyAfterUpload() throws Exception {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setItemId("item-1");
        post.setAuthorId("user-1");
        post.setKind("item");
        PostMedia attachment = new PostMedia();
        attachment.setId("media-1");
        attachment.setPostId("post-1");
        attachment.setOwnerId("user-1");
        attachment.setContentType("image/png");
        attachment.setSizeBytes(100L);
        attachment.setObjectKey("pending-post-media/media-1");
        attachment.setStatus("pending_upload");
        attachment.setModerationStatus("pending");
        attachment.setCover(true);
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(post));
        when(media.findByIdAndPostId("media-1", "post-1")).thenReturn(Optional.of(attachment));
        when(storage.head("pending-post-media/media-1")).thenReturn(new S3MediaStorage.ObjectMetadata(100L, "image/png"));
        when(storage.hasValidImageSignature("pending-post-media/media-1", "image/png")).thenReturn(true);
        when(storage.finalizeUpload("pending-post-media/media-1", "media-1", "image/png"))
                .thenReturn("post-media/media-1");
        when(media.findByPostIdAndCoverTrue("post-1")).thenReturn(List.of());
        when(catalogClient.getItem("item-1", null)).thenReturn(new ObjectMapper().readTree(
                "{\"postId\":\"post-1\"}"));

        postMediaService.confirmImageUpload("post-1", "media-1", "user-1");

        verify(catalogClient).updateItemCover(
                "item-1", "post-1", "/api/posts/post-1/media/media-1/content");
        assertTrue("ready".equals(attachment.getStatus()));
        assertTrue(attachment.getCover());
        assertTrue("approved".equals(attachment.getModerationStatus()));
    }

    @Test
    void createsFreshReadUrlsForReadyMedia() {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        when(posts.findById("post-1")).thenReturn(Optional.of(post));
        PostMedia attachment = new PostMedia();
        attachment.setId("media-1");
        attachment.setPostId("post-1");
        attachment.setObjectKey("post-media/media-1");
        attachment.setStatus("ready");
        when(media.findByIdAndPostId("media-1", "post-1")).thenReturn(Optional.of(attachment));
        when(storage.presignRead("post-media/media-1")).thenReturn("https://read.example/fresh");

        assertTrue(postMediaService.mediaContentUrl("post-1", "media-1").equals("https://read.example/fresh"));
    }

    @Test
    void replacesCatalogCoverOnlyWhenNewCoverIsApproved() throws Exception {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setItemId("item-1");
        PostMedia pendingCover = new PostMedia();
        pendingCover.setId("media-1");
        pendingCover.setPostId("post-1");
        pendingCover.setStatus("ready");
        pendingCover.setCover(true);
        pendingCover.setModerationStatus("pending");
        when(media.findByIdForUpdate("media-1")).thenReturn(Optional.of(pendingCover));
        when(posts.findById("post-1")).thenReturn(Optional.of(post));
        when(catalogClient.getItem("item-1", null)).thenReturn(new ObjectMapper().readTree(
                "{\"postId\":\"post-1\"}"));
        PostMedia existingCover = new PostMedia();
        existingCover.setId("old-cover");
        existingCover.setCover(true);
        existingCover.setModerationStatus("approved");
        when(media.findByPostIdAndCoverTrue("post-1")).thenReturn(List.of(existingCover));

        postMediaService.moderateMedia("media-1", "admin-1", "approved", null, Instant.now());

        assertTrue("approved".equals(pendingCover.getModerationStatus()));
        assertFalse(existingCover.getCover());
        verify(catalogClient).updateItemCover("item-1", "post-1", "/api/posts/post-1/media/media-1/content");
    }

    @Test
    void deletesPostMediaRowsAndStoredObjects() {
        PostMedia image = new PostMedia();
        image.setObjectKey("post-media/image-1");
        PostMedia externalLink = new PostMedia();
        externalLink.setExternalUrl("https://example.com");
        List<PostMedia> attachments = List.of(image, externalLink);
        when(media.findByPostIdOrderByCreatedAtAsc("post-1")).thenReturn(attachments);

        postMediaService.deleteForPost("post-1");

        verify(media).deleteAll(attachments);
        verify(storage).delete("post-media/image-1");
    }

    @Test
    void allowsAdminToDeleteAnotherAuthorsMedia() throws Exception {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setAuthorId("post-author");
        post.setKind("item");
        PostMedia attachment = new PostMedia();
        attachment.setId("media-1");
        attachment.setOwnerId("media-author");
        attachment.setStatus("ready");
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(post));
        when(identityClient.getUser("admin-user"))
                .thenReturn(new ObjectMapper().readTree("{\"role\":\"admin\"}"));
        when(media.findByIdAndPostId("media-1", "post-1")).thenReturn(Optional.of(attachment));

        postMediaService.deleteMedia("post-1", "media-1", "admin-user");

        verify(media).delete(attachment);
    }

    @Test
    void clearsCatalogCoverWhenDeletingUploadedCover() throws Exception {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setItemId("item-1");
        post.setAuthorId("user-1");
        post.setKind("item");
        PostMedia attachment = new PostMedia();
        attachment.setId("media-1");
        attachment.setPostId("post-1");
        attachment.setOwnerId("user-1");
        attachment.setCover(true);
        attachment.setStatus("ready");
        attachment.setModerationStatus("approved");
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(post));
        when(media.findByIdAndPostId("media-1", "post-1")).thenReturn(Optional.of(attachment));
        when(catalogClient.getItem("item-1", null)).thenReturn(new ObjectMapper().readTree(
                "{\"postId\":\"post-1\"}"));

        postMediaService.deleteMedia("post-1", "media-1", "user-1");

        verify(catalogClient).updateItemCover("item-1", "post-1", "");
    }
}
