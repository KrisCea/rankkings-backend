package cl.rankkings.social_service.controller;

import cl.rankkings.social_service.dto.SocialDtos.NewCommentInput;
import cl.rankkings.social_service.dto.SocialDtos.NewPostInput;
import cl.rankkings.social_service.dto.SocialDtos.RatingInput;
import cl.rankkings.social_service.dto.SocialDtos.ShareInput;
import cl.rankkings.social_service.service.SocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SocialController {
    private final SocialService socialService;

    @GetMapping("/posts")
    public List<Map<String, Object>> feed(
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.feed(viewerId);
    }

    @GetMapping("/posts/featured")
    public List<Map<String, Object>> featured(
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.featured(viewerId);
    }

    @GetMapping("/posts/{id}")
    public Map<String, Object> get(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.getPost(id, viewerId);
    }

    @GetMapping("/users/{username}/posts")
    public List<Map<String, Object>> userPosts(
            @PathVariable String username,
            @RequestParam String kind,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.userPosts(username, kind, viewerId);
    }

    @GetMapping("/items/{itemId}/reviews")
    public List<Map<String, Object>> reviews(
            @PathVariable String itemId,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.itemReviews(itemId, viewerId);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @Valid @RequestBody NewPostInput input,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.createPost(input, viewerId);
    }

    @PostMapping("/posts/{id}/vote")
    public Map<String, Object> vote(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.toggleVote(id, viewerId);
    }

    @PutMapping("/posts/{id}/rating")
    public Map<String, Object> rating(
            @PathVariable String id,
            @Valid @RequestBody RatingInput input,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.rate(id, input, viewerId);
    }

    @DeleteMapping("/posts/{id}/rating")
    public Map<String, Object> unrate(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.removeRating(id, viewerId);
    }

    @PostMapping("/posts/{id}/share")
    public Map<String, Object> share(
            @PathVariable String id,
            @Valid @RequestBody ShareInput input,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.share(id, input, viewerId);
    }

    @GetMapping("/posts/{id}/comments")
    public List<Map<String, Object>> comments(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.comments(id, viewerId);
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> comment(
            @PathVariable String id,
            @Valid @RequestBody NewCommentInput input,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.addComment(id, input, viewerId);
    }

    @PostMapping("/comments/{id}/like")
    public Map<String, Object> like(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return socialService.toggleCommentLike(id, viewerId);
    }

    @GetMapping("/internal/users/{id}/posts/count")
    public Map<String, Long> count(
            @PathVariable String id,
            @RequestHeader(value = "X-Internal-Key", required = false) String key
    ) {
        return socialService.countPostsByAuthor(id, key);
    }
}
