package com.lan4jn.file.service;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileDomainSupportTest {

    @Test
    void shouldRetryTransientGetObjectFailures() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        ConfigService configService = mock(ConfigService.class);
        FileDomainSupport support = new FileDomainSupport(minioClient, configService);
        GetObjectResponse response = mock(GetObjectResponse.class);

        when(minioClient.getObject(any()))
                .thenThrow(new RuntimeException("A timeout exceeded while waiting to proceed with the request, please reduce your request rate"))
                .thenThrow(new RuntimeException("A timeout exceeded while waiting to proceed with the request, please reduce your request rate"))
                .thenReturn(response);

        InputStream stream = assertDoesNotThrow(() -> support.getFileStream("docs/readme.txt", "bucket-a", "bucket-a"));

        assertNotNull(stream);
        verify(minioClient, times(3)).getObject(any());
    }
}
