package cl.rankkings.social_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMediaPolicyTest {
    @Test
    void acceptsSupportedImagesWithinTheSizeLimit() {
        PostMediaPolicy.validateImage("image/png", PostMediaPolicy.MAX_IMAGE_SIZE_BYTES);
    }

    @Test
    void rejectsSvgAndOversizedImages() {
        assertBadRequest(() -> PostMediaPolicy.validateImage("image/svg+xml", 100));
        assertBadRequest(() -> PostMediaPolicy.validateImage("image/jpeg", PostMediaPolicy.MAX_IMAGE_SIZE_BYTES + 1));
    }

    @Test
    void checksImageFileSignatures() {
        assertTrue(PostMediaPolicy.hasValidImageSignature("image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}));
        assertFalse(PostMediaPolicy.hasValidImageSignature("image/png", new byte[]{1, 2, 3}));
    }

    @Test
    void acceptsOnlyHttpsExternalLinksWithoutCredentialsOrLocalhost() {
        assertEquals("https://example.com/watch?v=1",
                PostMediaPolicy.normalizeExternalUrl("https://example.com/watch?v=1"));
        assertBadRequest(() -> PostMediaPolicy.normalizeExternalUrl("http://example.com/video"));
        assertBadRequest(() -> PostMediaPolicy.normalizeExternalUrl("javascript:alert(1)"));
        assertBadRequest(() -> PostMediaPolicy.normalizeExternalUrl("https://localhost/video"));
        assertBadRequest(() -> PostMediaPolicy.normalizeExternalUrl("https://user@example.com/video"));
    }

    private void assertBadRequest(Runnable operation) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
