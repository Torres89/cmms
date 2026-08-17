package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.File;
import com.grash.utils.Helper;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * S3 (or any S3-compatible store — Cloudflare R2, Backblaze B2, Wasabi).
 * <p>
 * This is the storage backend for hosted tiers. It removes the MinIO container
 * and, more importantly, the durability risk of keeping a customer's entire
 * manual library on one EBS volume behind a nightly sync. R2 is worth
 * preferring over S3 specifically because of video: zero egress fees, which
 * matters when technicians re-watch the same training clips.
 * <p>
 * It uses the MinIO Java SDK, which speaks plain S3 — so this is the same
 * client the MINIO backend uses, pointed at a real endpoint with a region.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service implements StorageService {

    @Value("${storage.s3.endpoint:}")
    private String endpoint;
    @Value("${storage.s3.region:}")
    private String region;
    @Value("${storage.s3.bucket:}")
    private String bucket;
    @Value("${storage.s3.access-key:}")
    private String accessKey;
    @Value("${storage.s3.secret-key:}")
    private String secretKey;
    /**
     * R2 and most S3-compatible stores want path-style addressing; real AWS S3
     * is happy either way.
     */
    @Value("${storage.s3.path-style:true}")
    private boolean pathStyle;

    @Value("${storage.inline-max-bytes:33554432}")
    private long inlineMaxBytes;

    @Override
    public long maxInMemoryDownloadBytes() {
        return inlineMaxBytes;
    }

    private MinioClient client;
    private boolean configured = false;

    @PostConstruct
    private void init() {
        if (endpoint.isEmpty() || bucket.isEmpty() || accessKey.isEmpty() || secretKey.isEmpty()) {
            log.info("S3 storage is not configured; the S3 backend will refuse requests if selected");
            return;
        }
        try {
            MinioClient.Builder builder = MinioClient.builder()
                    .endpoint(new URI(endpoint).toURL())
                    .credentials(accessKey, secretKey);
            if (!region.isEmpty()) {
                builder.region(region);
            }
            client = builder.build();
            // The SDK defaults to path-style; virtual-hosted style is opt-in.
            if (pathStyle) {
                client.disableVirtualStyleEndpoint();
            } else {
                client.enableVirtualStyleEndpoint();
            }
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                throw new CustomException("S3 bucket '" + bucket + "' does not exist",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            configured = true;
        } catch (CustomException e) {
            throw e;
        } catch (URISyntaxException e) {
            throw new CustomException("Invalid S3 endpoint: " + endpoint, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            throw new CustomException("Error configuring S3: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void checkIfConfigured() {
        if (!configured) {
            throw new CustomException("S3 storage is not configured. Set STORAGE_S3_ENDPOINT, "
                    + "STORAGE_S3_BUCKET, STORAGE_S3_ACCESS_KEY and STORAGE_S3_SECRET_KEY.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        checkIfConfigured();
        Helper helper = new Helper();
        String filePath = folder + "/" + helper.generateString() + " " + file.getOriginalFilename();
        try (InputStream in = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return filePath;
        } catch (Exception e) {
            throw new CustomException(e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Override
    public byte[] download(String filePath) {
        checkIfConfigured();
        assertSafeToBuffer(filePath);
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(filePath).build());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new CustomException("Error retrieving file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public byte[] download(File file) {
        return download(file.getPath());
    }

    @Override
    public long size(String filePath) {
        checkIfConfigured();
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(filePath).build());
            return stat.size();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public InputStream stream(String filePath, long start, long endInclusive) {
        checkIfConfigured();
        try {
            GetObjectArgs.Builder args = GetObjectArgs.builder().bucket(bucket).object(filePath).offset(start);
            if (endInclusive >= start) {
                args.length(endInclusive - start + 1);
            }
            return client.getObject(args.build());
        } catch (Exception e) {
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
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(filePath)
                    .expiry(Math.toIntExact(expirationMinutes), TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw new CustomException("Could not sign a URL for " + filePath,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
