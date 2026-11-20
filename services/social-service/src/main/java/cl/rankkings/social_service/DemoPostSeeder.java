package cl.rankkings.social_service;

import cl.rankkings.social_service.model.PostComment;
import cl.rankkings.social_service.model.PostRating;
import cl.rankkings.social_service.model.PostVote;
import cl.rankkings.social_service.model.SocialPost;
import cl.rankkings.social_service.repository.CommentRepository;
import cl.rankkings.social_service.repository.PostRepository;
import cl.rankkings.social_service.repository.RatingRepository;
import cl.rankkings.social_service.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoPostSeeder implements CommandLineRunner {
    private final PostRepository posts;
    private final VoteRepository votes;
    private final RatingRepository ratings;
    private final CommentRepository comments;

    @Override
    public void run(String... args) {
        List<DemoPost> samples = List.of(
                new DemoPost("item-101", "demo-user-ana", "Volví a este álbum y sigue sonando como un viaje completo.", 1),
                new DemoPost("item-110", "demo-user-mateo", "Un disco que mezcla emoción y energía de principio a fin.", 2),
                new DemoPost("item-201", "demo-user-camila", "La ciencia ficción también puede ser una historia muy íntima.", 3),
                new DemoPost("item-202", "demo-user-ana", "Una aventura visual llena de detalles que dan ganas de volver a verla.", 4),
                new DemoPost("item-301", "demo-user-mateo", "Una lectura breve que deja mucho para conversar.", 5),
                new DemoPost("item-401", "demo-user-camila", "El mundo invita a explorar y cada descubrimiento se siente ganado.", 6),
                new DemoPost("item-402", "demo-user-ana", "Perfecto para desconectarse un rato y avanzar a tu propio ritmo.", 7),
                new DemoPost("item-501", "demo-user-mateo", "Un episodio entretenido para acompañar el camino a casa.", 8),
                new DemoPost("item-601", "demo-user-camila", "Me encanta cómo esta obra transforma una escena cotidiana en algo inolvidable.", 9),
                new DemoPost("item-701", "demo-user-ana", "Buena opción para una tarde de estrategia y revancha amistosa.", 10),
                new DemoPost("item-801", "demo-user-mateo", "Una noche en vivo siempre se recuerda distinto a escuchar el disco.", 11),
                new DemoPost("item-120", "demo-user-camila", "Un álbum con una identidad muy marcada y muchos detalles para descubrir.", 12),
                new DemoPost("item-130", "demo-user-ana", "Me gusta cómo combina estilos distintos sin perder energía.", 13),
                new DemoPost("item-140", "demo-user-mateo", "La producción y los cambios de atmósfera hacen que funcione como una obra completa.", 14),
                new DemoPost("item-204", "demo-user-camila", "Una historia de ciencia ficción que pone las emociones en primer plano.", 15),
                new DemoPost("item-205", "demo-user-ana", "Una película impredecible, divertida y sorprendentemente cercana.", 16),
                new DemoPost("item-302", "demo-user-mateo", "La construcción del mundo hace que quieras seguir leyendo sobre su universo.", 17),
                new DemoPost("item-303", "demo-user-camila", "Una saga familiar que siempre deja nuevas conexiones al volver a ella.", 18),
                new DemoPost("item-403", "demo-user-ana", "Un desafío exigente que transmite muy bien el esfuerzo de seguir avanzando.", 19),
                new DemoPost("item-406", "demo-user-mateo", "Explorar a tu propio ritmo es una de las mejores partes de esta aventura.", 20),
                new DemoPost("item-702", "demo-user-camila", "Reglas fáciles de aprender y decisiones interesantes en cada partida.", 21)
        );

        int index = 0;
        for (DemoPost sample : samples) {
            if (posts.existsByItemIdAndKind(sample.itemId(), "item")) {
                index++;
                continue;
            }

            String postId = "demo-post-" + sample.itemId();
            if (posts.existsById(postId)) {
                index++;
                continue;
            }

            SocialPost post = new SocialPost();
            post.setId(postId);
            post.setAuthorId(sample.authorId());
            post.setItemId(sample.itemId());
            post.setKind("item");
            post.setCaption(sample.caption());
            post.setCreatedAt(Instant.now().minus(sample.daysAgo(), ChronoUnit.DAYS));
            posts.save(post);

            seedVote(postId, sample, index);
            if (sample.itemId().equals("item-201") || sample.itemId().equals("item-301")
                    || sample.itemId().equals("item-401")) {
                seedReview(postId, sample);
            }
            index++;
        }
    }

    private void seedVote(String postId, DemoPost sample, int index) {
        PostVote vote = new PostVote();
        vote.setId("demo-vote-" + index);
        vote.setPostId(postId);
        vote.setUserId(sample.authorId().equals("demo-user-ana") ? "demo-user-mateo" : "demo-user-ana");
        votes.save(vote);
    }

    private void seedReview(String postId, DemoPost sample) {
        String reviewerId = sample.authorId().equals("demo-user-ana") ? "demo-user-mateo" : "demo-user-ana";
        PostComment review = new PostComment();
        review.setId("demo-review-" + sample.itemId());
        review.setPostId(postId);
        review.setAuthorId(reviewerId);
        review.setKind("review");
        review.setText("Me dejó pensando después de terminarla. La recomiendo si buscas algo que invite a conversar.");
        comments.save(review);

        PostRating rating = new PostRating();
        rating.setId("demo-rating-" + sample.itemId());
        rating.setPostId(postId);
        rating.setUserId(reviewerId);
        rating.setScore(8.5);
        ratings.save(rating);
    }

    private record DemoPost(String itemId, String authorId, String caption, int daysAgo) {}
}
