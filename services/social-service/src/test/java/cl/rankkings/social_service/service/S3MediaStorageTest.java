package cl.rankkings.social_service.service;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class S3MediaStorageTest {
    @Test
    void presignedUploadBindsContentLengthTypeAndSingleUseCondition() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret"));
        try (S3Client client = S3Client.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build();
             S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build()) {
            S3MediaStorage storage = new S3MediaStorage(client, presigner, "rankkings-test");
            String query = URI.create(storage.presignUpload("post-media/id", "image/png", 1234).url())
                    .getRawQuery();
            String signedHeaders = Arrays.stream(query.split("&"))
                    .filter(part -> part.startsWith("X-Amz-SignedHeaders="))
                    .map(part -> URLDecoder.decode(part.substring(part.indexOf('=') + 1), StandardCharsets.UTF_8))
                    .findFirst()
                    .orElseThrow();

            assertTrue(signedHeaders.contains("content-length"), signedHeaders);
            assertTrue(signedHeaders.contains("content-type"), signedHeaders);
            assertTrue(signedHeaders.contains("if-none-match"), signedHeaders);
        }
    }
}
