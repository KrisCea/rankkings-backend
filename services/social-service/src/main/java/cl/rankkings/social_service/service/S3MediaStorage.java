package cl.rankkings.social_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;

@Service
public class S3MediaStorage {
    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(5);
    private static final Duration READ_URL_TTL = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3MediaStorage(
            S3Client s3Client,
            S3Presigner presigner,
            @Value("${rankkings.media.s3.bucket:}") String bucket
    ) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public SignedUrl presignUpload(String key, String contentType, long contentLength) {
        requireBucket();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .ifNoneMatch("*")
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putObjectRequest)
                .build());
        return new SignedUrl(presigned.url().toExternalForm(), presigned.expiration().toString());
    }

    public String presignRead(String key) {
        requireBucket();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(READ_URL_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build());
        return presigned.url().toExternalForm();
    }

    public ObjectMetadata head(String key) {
        requireBucket();
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        return new ObjectMetadata(response.contentLength(), response.contentType());
    }

    public boolean hasValidImageSignature(String key, String contentType) {
        requireBucket();
        try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=0-11")
                .build())) {
            return PostMediaPolicy.hasValidImageSignature(contentType, object.readNBytes(12));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo verificar el archivo subido", ex);
        }
    }

    public void delete(String key) {
        requireBucket();
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    public String finalizeUpload(String pendingKey, String mediaId, String contentType) {
        requireBucket();
        String finalKey = "post-media/" + mediaId;
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(pendingKey)
                .bucket(bucket)
                .key(finalKey)
                .metadataDirective(MetadataDirective.REPLACE)
                .contentType(contentType)
                .build());
        return finalKey;
    }

    private void requireBucket() {
        if (bucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El almacenamiento multimedia no está configurado");
        }
    }

    public record SignedUrl(String url, String expiresAt) {}

    public record ObjectMetadata(long contentLength, String contentType) {}
}
