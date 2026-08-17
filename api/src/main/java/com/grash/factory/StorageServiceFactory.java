package com.grash.factory;

import com.grash.model.enums.StorageType;
import com.grash.service.GCPService;
import com.grash.service.LocalStorageService;
import com.grash.service.MinioService;
import com.grash.service.S3Service;
import com.grash.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class StorageServiceFactory {
    @Value("${storage.type}")
    private StorageType storageType;

    private final GCPService gcpService;
    private final MinioService minioService;
    private final S3Service s3Service;
    private final LocalStorageService localStorageService;

    public StorageService getStorageService() {
        switch (storageType) {
            case GCP:
                return gcpService;
            case S3:
                return s3Service;
            case LOCAL:
                return localStorageService;
            default:
                return minioService;
        }
    }

    public StorageType getStorageType() {
        return storageType;
    }
}
