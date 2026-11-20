package cl.rankkings.social_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Set;

final class PostMediaPolicy {
    static final int MAX_ATTACHMENTS_PER_POST = 4;
    static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    static final long MAX_TOTAL_SIZE_BYTES = 20L * 1024 * 1024;
    static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private PostMediaPolicy() {}

    static void validateImage(String contentType, long sizeBytes) {
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de imagen no permitido");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen debe pesar hasta 10 MB");
        }
    }

    static boolean hasValidImageSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> header.length >= 3
                    && (header[0] & 0xff) == 0xff
                    && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
            case "image/png" -> header.length >= 8
                    && (header[0] & 0xff) == 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G'
                    && (header[4] & 0xff) == 0x0d
                    && (header[5] & 0xff) == 0x0a
                    && (header[6] & 0xff) == 0x1a
                    && (header[7] & 0xff) == 0x0a;
            case "image/webp" -> header.length >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P';
            default -> false;
        };
    }

    static String normalizeExternalUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL externa inválida");
        }

        try {
            URI uri = URI.create(value.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getHost().equalsIgnoreCase("localhost")
                    || uri.getHost().endsWith(".localhost")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten enlaces HTTPS públicos");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL externa inválida");
        }
    }
}
