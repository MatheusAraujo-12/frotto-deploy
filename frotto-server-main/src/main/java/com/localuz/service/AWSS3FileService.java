package com.localuz.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AWSS3FileService {

    @Value("${aws.bucketName}")
    private String bucketName;

    @Autowired
    private AmazonS3 s3Client;

    public String uploadFile(MultipartFile file, String uniqueIdentifier) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        String extension = extensionFromContentType(file.getContentType());
        if (extension.isEmpty()) {
            extension = extensionFromFilename(file.getOriginalFilename());
        }
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String uniqueFileName = System.currentTimeMillis() + "_" + uniqueIdentifier + suffix;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (file.getContentType() != null && !file.getContentType().isEmpty()) {
            metadata.setContentType(file.getContentType());
        }

        try (InputStream input = file.getInputStream()) {
            s3Client.putObject(new PutObjectRequest(bucketName, uniqueFileName, input, metadata));
            return uniqueFileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload file to S3", e);
        }
    }

    public String uploadBase64(String base64Data, String uniqueIdentifier) {
        if (base64Data == null || base64Data.trim().isEmpty()) {
            return "";
        }

        String trimmed = base64Data.trim();
        String contentType = null;
        String payload = trimmed;

        if (trimmed.startsWith("data:")) {
            int commaIndex = trimmed.indexOf(',');
            if (commaIndex > -1) {
                String meta = trimmed.substring(5, commaIndex);
                payload = trimmed.substring(commaIndex + 1);
                String[] metaParts = meta.split(";");
                if (metaParts.length > 0) {
                    contentType = metaParts[0];
                }
            }
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid base64 image data", e);
        }

        if (contentType == null || contentType.isEmpty()) {
            contentType = detectContentType(decoded);
        }

        String extension = extensionFromContentType(contentType);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String uniqueFileName = System.currentTimeMillis() + "_" + uniqueIdentifier + suffix;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(decoded.length);
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(decoded);
        s3Client.putObject(new PutObjectRequest(bucketName, uniqueFileName, input, metadata));
        return uniqueFileName;
    }

    public byte[] downloadFile(String fileName) {
        S3Object s3Object = s3Client.getObject(bucketName, fileName);
        S3ObjectInputStream inputStream = s3Object.getObjectContent();
        try {
            byte[] content = IOUtils.toByteArray(inputStream);
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String deleteFile(String fileName) {
        s3Client.deleteObject(bucketName, fileName);
        return fileName + " removed ...";
    }

    private String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        switch (contentType) {
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/webp":
                return "webp";
            case "image/heic":
                return "heic";
            default:
                return "";
        }
    }

    private String extensionFromFilename(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private String detectContentType(byte[] data) {
        if (data == null || data.length < 3) {
            return null;
        }
        // JPEG magic: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG magic: 89 50 4E 47 0D 0A 1A 0A
        if (
            data.length >= 8 &&
            (data[0] & 0xFF) == 0x89 &&
            (data[1] & 0xFF) == 0x50 &&
            (data[2] & 0xFF) == 0x4E &&
            (data[3] & 0xFF) == 0x47 &&
            (data[4] & 0xFF) == 0x0D &&
            (data[5] & 0xFF) == 0x0A &&
            (data[6] & 0xFF) == 0x1A &&
            (data[7] & 0xFF) == 0x0A
        ) {
            return "image/png";
        }
        // WEBP magic: "RIFF....WEBP"
        if (
            data.length >= 12 &&
            data[0] == 'R' &&
            data[1] == 'I' &&
            data[2] == 'F' &&
            data[3] == 'F' &&
            data[8] == 'W' &&
            data[9] == 'E' &&
            data[10] == 'B' &&
            data[11] == 'P'
        ) {
            return "image/webp";
        }
        return null;
    }
}
