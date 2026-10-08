package com.StreamingApp.StreamingBE.audiobooks;

import io.minio.*;
import io.minio.http.Method;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AudiobookMedia {
    private final Path root;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    public AudiobookMedia(@Value("${audiobooks.media-root:./media/audiobooks}") String root,
            @Value("${media.endpoint:http://localhost:9000}") String endpoint,
            @Value("${media.access-key:}") String accessKey, @Value("${media.secret-key:}") String secretKey) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public void validateSource(String source) {
        if (source.startsWith("s3://")) {
            URI uri = URI.create(source);
            if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2 || uri.getQuery() != null) {
                throw new IllegalArgumentException("Expected s3://bucket/object");
            }
        } else {
            Path file = root.resolve(source).normalize();
            if (Path.of(source).isAbsolute() || source.contains(":") || !file.startsWith(root)) {
                throw new IllegalArgumentException("Audio files must be inside the audiobook media directory");
            }
        }
    }

    public ResponseEntity<Resource> audio(String source) {
        try {
            validateSource(source);
            if (source.startsWith("s3://")) {
                if (accessKey.isBlank() || secretKey.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is not configured");
                }
                URI uri = URI.create(source);
                var client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
                String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                        .bucket(uri.getHost()).object(uri.getPath().substring(1)).expiry(12, TimeUnit.HOURS).build());
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(URI.create(url))
                        .cacheControl(CacheControl.noStore()).build();
            }
            Path file = root.resolve(source).normalize();
            if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found");
            // Resolve symlinks too, so a catalogue entry cannot expose files outside the media directory.
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found");
            }
            MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().contentType(type).header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(new FileSystemResource(file));
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Audiobook storage is unavailable", error);
        }
    }
}
