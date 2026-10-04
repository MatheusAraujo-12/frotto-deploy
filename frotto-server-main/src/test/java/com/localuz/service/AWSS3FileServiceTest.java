package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.amazonaws.services.s3.AmazonS3;
import com.localuz.service.storage.LegacyS3FileStorageService;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The AWS SDK only rejects null keys: deleteObject(bucket, "") is sent as a DELETE on the bucket root, which is the
 * HTTP form of DeleteBucket. Blank keys must never reach the S3 client, through any flow.
 */
class AWSS3FileServiceTest {

    private AmazonS3 client;
    private AWSS3FileService service;

    @BeforeEach
    void setUp() {
        client = mock(AmazonS3.class);
        service = new AWSS3FileService(client);
        ReflectionTestUtils.setField(service, "bucketName", "localuz-locamais");
    }

    @Test
    void nullEmptyOrBlankKeysNeverCallTheS3Client() {
        for (String key : Arrays.asList(null, "", " ", "   ", "\t", "\n")) {
            service.deleteFile(key);
        }

        verifyNoInteractions(client);
    }

    @Test
    void validKeysAreStillDeleted() {
        service.deleteFile("1771248962905_USER_4.png");
        service.deleteFile("1774293055076_DOC_20.pdf");

        verify(client).deleteObject("localuz-locamais", "1771248962905_USER_4.png");
        verify(client).deleteObject("localuz-locamais", "1774293055076_DOC_20.pdf");
    }

    @Test
    void legacyAdapterAlsoStopsBlankKeysBeforeTheLegacyService() {
        AWSS3FileService legacy = mock(AWSS3FileService.class);
        LegacyS3FileStorageService adapter = new LegacyS3FileStorageService(legacy);

        for (String key : Arrays.asList(null, "", "  ")) {
            adapter.delete(key);
        }
        adapter.delete("1700000000000_01.png");

        verify(legacy, never()).deleteFile(null);
        verify(legacy, never()).deleteFile("");
        verify(legacy, never()).deleteFile("  ");
        verify(legacy).deleteFile("1700000000000_01.png");
        assertThat(service.deleteFile("")).isEmpty();
        verify(client, never()).deleteObject(anyString(), anyString());
    }
}
