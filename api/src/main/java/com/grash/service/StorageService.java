package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.File;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface StorageService {
    /**
     * Hard ceiling on what {@link #download(String)} will pull into JVM heap.
     * <p>
     * {@code download} returns a {@code byte[]}, so every byte of the file
     * lands on the heap. That is fine for a 5 MB manual and an OOM for a
     * 500 MB training video — guaranteed once two technicians watch at once.
     * Anything above this must be served with {@link #generateSignedUrl}, which
     * lets the browser or phone fetch directly from the object store and get
     * proper HTTP range support for seeking.
     */
    long MAX_IN_MEMORY_DOWNLOAD_BYTES = 32L * 1024 * 1024;

    /**
     * Uploads a file to the storage and returns the public URL.
     *
     * @param file   The file to be uploaded.
     * @param folder The folder where the file should be uploaded.
     * @return The file Path of the uploaded file.
     */
    String upload(MultipartFile file, String folder);

    /**
     * Downloads a file from the storage using its file path.
     * <p>
     * Only for small files — see {@link #MAX_IN_MEMORY_DOWNLOAD_BYTES}.
     *
     * @param filePath The path of the file to be downloaded.
     * @return A byte array of the file content.
     */
    byte[] download(String filePath);

    /**
     * Downloads a file from the storage using a File object.
     * <p>
     * Only for small files — see {@link #MAX_IN_MEMORY_DOWNLOAD_BYTES}.
     *
     * @param file The File object containing the URL and metadata.
     * @return A byte array of the file content.
     */
    byte[] download(File file);

    String generateSignedUrl(File file, long expirationMinutes);

    String generateSignedUrl(String filePath, long expirationMinutes);

    /**
     * Size of the stored object in bytes, or -1 when the backend can't say.
     */
    default long size(String filePath) {
        return -1;
    }

    /**
     * Open a byte range for streaming. Backends that serve range requests
     * themselves (S3, MinIO, GCS via presigned URLs) don't need this — it
     * exists so the {@code LOCAL} tier can stream video without buffering.
     */
    default InputStream stream(String filePath, long start, long endInclusive) {
        throw new CustomException("This storage backend does not support range streaming",
                HttpStatus.NOT_IMPLEMENTED);
    }

    /**
     * Overridable so a deployment can tune {@code storage.inline-max-bytes}.
     */
    default long maxInMemoryDownloadBytes() {
        return MAX_IN_MEMORY_DOWNLOAD_BYTES;
    }

    /**
     * Guard used by every backend before buffering a file into heap.
     */
    default void assertSafeToBuffer(String filePath) {
        long size = size(filePath);
        if (size > maxInMemoryDownloadBytes()) {
            throw new CustomException(
                    "This file is too large to serve through the API (" + size + " bytes). "
                            + "Use a signed URL so it is fetched directly from storage.",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    default String uploadAndSign(MultipartFile file, String folder) {
        return generateSignedUrl(upload(file, folder), 10);
    }
}
