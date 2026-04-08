package com.lan4jn.file.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinioConfigTest {

    @Test
    void shouldCreateMinioClientWithExplicitTimeoutConfiguration() {
        MinioConfig config = new MinioConfig();
        ReflectionTestUtils.setField(config, "endpoint", "http://127.0.0.1:9000");
        ReflectionTestUtils.setField(config, "accessKey", "minioadmin");
        ReflectionTestUtils.setField(config, "secretKey", "minioadmin");
        ReflectionTestUtils.setField(config, "bucketName", "file-manager");
        ReflectionTestUtils.setField(config, "connectTimeoutMs", 30000L);
        ReflectionTestUtils.setField(config, "writeTimeoutMs", 300000L);
        ReflectionTestUtils.setField(config, "readTimeoutMs", 300000L);

        MinioClient client = assertDoesNotThrow(config::minioClient);

        assertNotNull(client);
    }
}
