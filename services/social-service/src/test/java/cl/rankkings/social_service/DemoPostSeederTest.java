package cl.rankkings.social_service;

import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import cl.rankkings.social_service.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoPostSeederTest {
    @Mock private PostRepository posts;
    @Mock private VoteRepository votes;
    @Mock private RatingRepository ratings;
    @Mock private CommentRepository comments;
    @InjectMocks private DemoPostSeeder seeder;

    @Test
    void insertsDemoPostsAndInteractionsForTheSeededCatalogItems() {
        when(posts.existsByItemIdAndKind(anyString(), anyString())).thenReturn(false);
        when(posts.existsById(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<SocialPost> saved = ArgumentCaptor.forClass(SocialPost.class);
        verify(posts, times(21)).save(saved.capture());
        assertEquals("item", saved.getAllValues().getFirst().getKind());
        assertEquals("item-101", saved.getAllValues().getFirst().getItemId());
        verify(votes, times(21)).save(org.mockito.ArgumentMatchers.any());
        verify(comments, times(3)).save(org.mockito.ArgumentMatchers.any());
        verify(ratings, times(3)).save(org.mockito.ArgumentMatchers.any());
    }
}
