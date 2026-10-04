package com.localuz.config;

import com.localuz.service.storage.FileUrlSigner;
import com.localuz.service.storage.LocalFileStorageService;
import com.localuz.service.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * File storage wiring. The application never fails to start because of storage: in local mode an unavailable
 * volume is reported here (ERROR) and uploads are refused with 503 until it is fixed. No secret is ever logged.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    private final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    private final StorageProperties properties;
    private final LocalFileStorageService localStorage;
    private final FileUrlSigner signer;

    public StorageConfiguration(StorageProperties properties, LocalFileStorageService localStorage, FileUrlSigner signer) {
        this.properties = properties;
        this.localStorage = localStorage;
        this.signer = signer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportStorageStatus() {
        if (!properties.isLocalMode()) {
            log.info("File storage mode: s3 (legacy)");
            return;
        }
        LocalFileStorageService.Availability availability = localStorage.checkAvailability();
        boolean baseUrlConfigured = properties.getFiles().getBaseUrl() != null && !properties.getFiles().getBaseUrl().isBlank();
        if (availability == LocalFileStorageService.Availability.AVAILABLE) {
            log.info("File storage mode: local (root={}, available)", properties.getRoot());
        } else {
            log.error(
                "File storage mode: local but storage is {} (root={}); uploads will be refused. " +
                "Check the bind mount and the {} sentinel file.",
                availability,
                properties.getRoot(),
                LocalFileStorageService.SENTINEL
            );
        }
        if (!signer.isConfigured() || !baseUrlConfigured) {
            log.error(
                "File storage mode: local but signed URLs are not configured (signingSecretConfigured={}, baseUrlConfigured={}); " +
                "local files cannot be served",
                signer.isConfigured(),
                baseUrlConfigured
            );
        }
        log.info("Legacy S3 read fallback: {}", properties.getLegacyS3().isReadFallbackEnabled() ? "enabled (read-only)" : "disabled");
    }
}
