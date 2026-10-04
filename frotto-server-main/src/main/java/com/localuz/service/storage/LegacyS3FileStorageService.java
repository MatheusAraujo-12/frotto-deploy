package com.localuz.service.storage;

import com.localuz.service.AWSS3FileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Legacy AWS S3 storage ({@code frotto.storage.mode=s3}): pure delegation to the existing {@link AWSS3FileService},
 * so bucket, credentials and the historical key format ({@code {millis}_{identifier}.{ext}}) stay exactly as they
 * are in production. Only {@link FileStorageGateway} calls it, and never in local mode.
 *
 * <p>It does not implement {@link FileStorageService}: historical keys need an owner identifier (e.g. USER_7) and
 * S3 objects are read by browsers from the public bucket, never by the backend.
 */
@Service
public class LegacyS3FileStorageService {

    private final AWSS3FileService s3;

    public LegacyS3FileStorageService(AWSS3FileService s3) {
        this.s3 = s3;
    }

    /** @return the historical key, or an empty string for an empty file (same contract as before). */
    public String store(MultipartFile file, String legacyIdentifier) {
        return s3.uploadFile(file, legacyIdentifier);
    }

    /** Base64 payload (optionally a data: URL), as sent by the mobile camera flow. Same contract as before. */
    public String storeBase64(String base64Data, String legacyIdentifier) {
        return s3.uploadBase64(base64Data, legacyIdentifier);
    }

    /** Blank keys (photo-less records) never reach S3; see {@link AWSS3FileService#deleteFile}. */
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        s3.deleteFile(key);
    }
}
