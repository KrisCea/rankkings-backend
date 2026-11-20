package cl.rankkings.social_service.service;

import cl.rankkings.social_service.dto.SocialDtos.ImageUploadInput;
import cl.rankkings.social_service.model.PostMedia;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.PostMediaRepository;
import cl.rankkings.social_service.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMediaServiceTest {
    @Mock private PostRepository posts;
    @Mock private PostMediaRepository media;
    @Mock private S3MediaStorage storage;
    @InjectMocks private PostMediaService postMediaService;

    @Test
    void storesUploadedCoverAndReplacesThePreviousCover() {
        SocialPost post = new SocialPost();
        post.setId("post-1");
        post.setAuthorId("user-1");
        post.setKind("item");
        PostMedia previousCover = new PostMedia();
        previousCover.setId("previous-cover");
        previousCover.setCover(true);
        when(posts.findByIdForUpdate("post-1")).thenReturn(Optional.of(post));
        when(media.findByPostIdAndCoverTrue("post-1")).thenReturn(List.of(previousCover));
        when(media.findByPostIdOrderByCreatedAtAsc("post-1")).thenReturn(List.of());
        when(storage.presignUpload(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("image/png"), org.mockito.ArgumentMatchers.eq(100L)))
                .thenReturn(new S3MediaStorage.SignedUrl("https://upload.example/image", "expires"));

        postMediaService.prepareImageUpload("post-1", new ImageUploadInput("image/png", 100L, true), "user-1");

        assertFalse(previousCover.getCover());
        ArgumentCaptor<PostMedia> uploaded = ArgumentCaptor.forClass(PostMedia.class);
        verify(media, times(2)).save(uploaded.capture());
        assertSame(previousCover, uploaded.getAllValues().get(0));
        assertTrue(uploaded.getAllValues().get(1).getCover());
        assertTrue(uploaded.getAllValues().get(1).getObjectKey().startsWith("pending-post-media/"));
    }
}
