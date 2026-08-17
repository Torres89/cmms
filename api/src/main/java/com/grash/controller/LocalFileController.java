package com.grash.controller;

import com.grash.exception.CustomException;
import com.grash.factory.StorageServiceFactory;
import com.grash.model.enums.StorageType;
import com.grash.service.LocalStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Serves files on the {@code LOCAL} storage tier through the HMAC-signed,
 * expiring links {@link LocalStorageService} mints.
 * <p>
 * Range requests are honoured so a technician can seek and scrub a training
 * video, and nothing is ever buffered into heap — the response streams the
 * requested window straight off disk. On a deployment fronted by Caddy, the
 * same paths can be served by Caddy with {@code forward_auth} against this
 * endpoint, keeping the JVM out of the data path completely.
 */
@RestController
@RequestMapping("/files")
@Tag(name = "file")
@RequiredArgsConstructor
public class LocalFileController {

    private static final long CHUNK_SIZE = 4L * 1024 * 1024;

    private final StorageServiceFactory storageServiceFactory;
    private final LocalStorageService localStorageService;

    @GetMapping("/local")
    @PreAuthorize("permitAll()")
    public ResponseEntity<InputStreamResource> serve(
            @RequestParam("path") String path,
            @RequestParam("exp") long expiresAt,
            @RequestParam("sig") String signature,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        if (storageServiceFactory.getStorageType() != StorageType.LOCAL) {
            throw new CustomException("This deployment does not use local file storage",
                    HttpStatus.NOT_FOUND);
        }
        localStorageService.verifySignature(path, expiresAt, signature);

        long total = localStorageService.size(path);
        if (total < 0) {
            throw new CustomException("File not found", HttpStatus.NOT_FOUND);
        }

        Path resolved = localStorageService.resolve(path);
        String filename = resolved.getFileName().toString();
        MediaType contentType = guessContentType(filename);

        long start = 0;
        long end = total - 1;
        boolean partial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).split(",")[0].trim();
            int dash = spec.indexOf('-');
            try {
                if (dash == 0) {                       // suffix range: last N bytes
                    long suffix = Long.parseLong(spec.substring(1));
                    start = Math.max(0, total - suffix);
                } else {
                    start = Long.parseLong(spec.substring(0, dash));
                    String endPart = spec.substring(dash + 1);
                    if (!endPart.isEmpty()) {
                        end = Long.parseLong(endPart);
                    } else {
                        // Open-ended range: hand back a bounded chunk rather than
                        // the whole remainder of a large file.
                        end = Math.min(total - 1, start + CHUNK_SIZE - 1);
                    }
                }
            } catch (NumberFormatException e) {
                throw new CustomException("Malformed Range header", HttpStatus.BAD_REQUEST);
            }
            if (start >= total || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                        .build();
            }
            end = Math.min(end, total - 1);
            partial = true;
        }

        long length = end - start + 1;
        InputStream stream = localStorageService.stream(path, start, end);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(length);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePrivate());
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        if (partial) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
        }

        return new ResponseEntity<>(
                new InputStreamResource(new BoundedInputStream(stream, length)),
                headers,
                partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    private MediaType guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".mp4")) return MediaType.valueOf("video/mp4");
        if (lower.endsWith(".webm")) return MediaType.valueOf("video/webm");
        if (lower.endsWith(".mov")) return MediaType.valueOf("video/quicktime");
        if (lower.endsWith(".csv")) return MediaType.valueOf("text/csv");
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Caps a stream at a byte count so a range response never overruns.
     */
    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws java.io.IOException {
            if (remaining <= 0) return -1;
            int b = delegate.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }
    }
}
