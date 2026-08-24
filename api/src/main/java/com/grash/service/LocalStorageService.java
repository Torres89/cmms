package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.File;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.Base64;
import java.net.URLEncoder;

/**
 * Filesystem storage for the self-hosted tier — the customer's own PC or their
 * own cloud, where they own the hardware and the backup responsibility.
 * <p>
 * There are no presigned URLs on a filesystem, so this backend mints its own:
 * an HMAC-signed, expiring link to {@code /files/local}, which streams with
 * proper HTTP range support. In front of a real deployment, Caddy can serve
 * the same paths directly with a {@code forward_auth} check against the API,
 * keeping the JVM out of the data path entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalStorageService implements StorageService {

    @Value("${storage.local.base-path:}")
    private String basePathValue;
    /**
     * Public base URL the browser can reach the API on. Signed links are built
     * against it.
     */
    @Value("${api.host:}")
    private String apiHost;
    @Value("${storage.local.signing-key:}")
    private String signingKeyValue;
    /**
     * Read as a plain string rather than the enum: this bean is constructed on
     * every deployment, including ones whose storage type is something else
     * entirely, and a misspelt value must not fail the context here.
     */
    @Value("${storage.type:}")
    private String storageTypeValue;

    @Value("${storage.inline-max-bytes:33554432}")
    private long inlineMaxBytes;

    @Override
    public long maxInMemoryDownloadBytes() {
        return inlineMaxBytes;
    }

    private Path basePath;
    private byte[] signingKey;
    private boolean configured = false;

    @PostConstruct
    private void init() {
        if (basePathValue == null || basePathValue.isEmpty()) {
            log.info("Local storage is not configured; the LOCAL backend will refuse requests if selected");
            return;
        }
        basePath = Paths.get(basePathValue).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new CustomException("Cannot create local storage directory " + basePath,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String key = (signingKeyValue == null || signingKeyValue.isEmpty()) ? null : signingKeyValue;
        if (key == null) {
            // Only fatal when this backend is the one actually in use. The
            // base path above defaults to a real directory in docker-compose,
            // so without this check every deployment on minio/s3/gcp dies here
            // unless it sets a signing key for a backend it never touches.
            // checkIfConfigured() still refuses individual requests, so a
            // genuinely misconfigured LOCAL install fails loudly at the call.
            if (!"LOCAL".equalsIgnoreCase(storageTypeValue)) {
                log.info("Local storage has a base path but no signing key; leaving it unconfigured "
                        + "because STORAGE_TYPE={}", storageTypeValue);
                return;
            }
            throw new CustomException(
                    "STORAGE_LOCAL_SIGNING_KEY must be set when STORAGE_TYPE=local",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        signingKey = key.getBytes(StandardCharsets.UTF_8);
        configured = true;
    }

    private void checkIfConfigured() {
        if (!configured) {
            throw new CustomException("Local storage is not configured. Set STORAGE_LOCAL_PATH "
                    + "and STORAGE_LOCAL_SIGNING_KEY.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Resolve a stored path inside the base directory, refusing anything that
     * escapes it.
     */
    public Path resolve(String filePath) {
        checkIfConfigured();
        Path resolved = basePath.resolve(filePath).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new CustomException("Invalid file path", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        checkIfConfigured();
        Helper helper = new Helper();
        String filePath = folder + "/" + helper.generateString() + " " + file.getOriginalFilename();
        Path target = resolve(filePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CustomException("Could not store the file: " + e.getMessage(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return filePath;
    }

    @Override
    public byte[] download(String filePath) {
        assertSafeToBuffer(filePath);
        try {
            return Files.readAllBytes(resolve(filePath));
        } catch (IOException e) {
            throw new CustomException("Error retrieving file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public byte[] download(File file) {
        return download(file.getPath());
    }

    @Override
    public long size(String filePath) {
        try {
            return Files.size(resolve(filePath));
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public InputStream stream(String filePath, long start, long endInclusive) {
        Path path = resolve(filePath);
        try {
            InputStream in = Files.newInputStream(path, StandardOpenOption.READ);
            long skipped = 0;
            while (skipped < start) {
                long n = in.skip(start - skipped);
                if (n <= 0) break;
                skipped += n;
            }
            return in;
        } catch (IOException e) {
            throw new CustomException("Error streaming file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String generateSignedUrl(File file, long expirationMinutes) {
        return generateSignedUrl(file.getPath(), expirationMinutes);
    }

    @Override
    public String generateSignedUrl(String filePath, long expirationMinutes) {
        checkIfConfigured();
        long expiresAt = System.currentTimeMillis() / 1000L + expirationMinutes * 60L;
        String signature = sign(filePath, expiresAt);
        String base = (apiHost == null || apiHost.isEmpty()) ? "" : apiHost.replaceAll("/+$", "");
        return base + "/files/local?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8)
                + "&exp=" + expiresAt + "&sig=" + signature;
    }

    public String sign(String filePath, long expiresAt) {
        checkIfConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal((filePath + "\n" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new CustomException("Could not sign the file URL", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Constant-time check of a signed link.
     */
    public void verifySignature(String filePath, long expiresAt, String signature) {
        if (expiresAt < System.currentTimeMillis() / 1000L) {
            throw new CustomException("This link has expired", HttpStatus.FORBIDDEN);
        }
        byte[] expected = sign(filePath, expiresAt).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signature == null
                ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new CustomException("Invalid link signature", HttpStatus.FORBIDDEN);
        }
    }
}
