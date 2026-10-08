package com.StreamingApp.StreamingBE.movies;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybackService {
    private final MovieService movies;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    public PlaybackService(MovieService movies,
            @Value("${media.endpoint:http://localhost:9000}") String endpoint,
            @Value("${media.access-key:}") String accessKey,
            @Value("${media.secret-key:}") String secretKey) {
        this.movies = movies;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String playlist(int id) {
        String reference = movies.findById(id).hlsReference();
        if (reference == null || !reference.startsWith("s3://")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No private movie source configured");
        }
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is not configured");
        }
        try {
            URI source = URI.create(reference);
            String bucket = source.getHost();
            String object = source.getPath().substring(1);
            String folder = object.substring(0, object.lastIndexOf('/') + 1);
            MinioClient client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            String manifest;
            try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
                manifest = new String(stream.readNBytes(2_000_000), StandardCharsets.UTF_8);
            }
            if (!manifest.startsWith("#EXTM3U")) throw new IllegalArgumentException("Invalid playlist");
            StringBuilder result = new StringBuilder();
            for (String line : manifest.split("\\R")) {
                if (line.contains("URI=")) {
                    throw new IllegalArgumentException("Encrypted or alternate-track playlists are not supported yet");
                }
                if (!line.isBlank() && !line.startsWith("#")) {
                    String segment = line.trim();
                    if (segment.contains("..") || segment.contains(":") || segment.startsWith("/")
                            || !segment.endsWith(".ts")) {
                        throw new IllegalArgumentException("Expected a relative transport-stream segment");
                    }
                    line = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET).bucket(bucket).object(folder + segment)
                            .expiry(12, TimeUnit.HOURS).build());
                }
                result.append(line).append('\n');
            }
            return result.toString();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Movie storage is unavailable", exception);
        }
    }
}
