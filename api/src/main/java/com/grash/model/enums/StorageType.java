package com.grash.model.enums;

public enum StorageType {
    /**
     * Google Cloud Storage.
     */
    GCP,
    /**
     * A MinIO server. The historical default: an extra container holding the
     * files on the same box as everything else.
     */
    MINIO,
    /**
     * S3 or an S3-compatible object store (Cloudflare R2, Backblaze B2...).
     * The default for hosted tiers: no container, no RAM, durable, and
     * presigned URLs mean large files never pass through the JVM.
     */
    S3,
    /**
     * The local filesystem. For self-hosted installs on the customer's own
     * hardware, where they own the backup responsibility.
     */
    LOCAL
}
